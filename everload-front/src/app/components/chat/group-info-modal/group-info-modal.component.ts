import { Component, EventEmitter, HostListener, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { ChatService, ChatGroupDto, MemberDto } from '../../../services/chat.service';
import { AuthService } from '../../../services/auth.service';
import { NotificationService } from '../../../services/notification.service';

@Component({
  selector: 'app-group-info-modal',
  templateUrl: './group-info-modal.component.html',
  styleUrls: ['./group-info-modal.component.css']
})
export class GroupInfoModalComponent implements OnInit, OnChanges {
  @Input() group!: ChatGroupDto;
  @Output() close = new EventEmitter<void>();
  @Output() groupUpdated = new EventEmitter<void>();

  members: MemberDto[] = [];
  currentUser: string | null = null;
  /** Role of the current user as determined from the freshly loaded member list (more reliable than group.currentUserRole). */
  myRole: string | null = null;
  loading = false;
  actionLoading = false;

  // Edit group info
  editInfoMode = false;
  editName = '';
  editDescription = '';

  // Context menus
  openMenuForUser: string | null = null;

  // Add member panel
  addMemberMode = false;
  newMemberUsername = '';
  addMemberLoading = false;

  constructor(
    private chatService: ChatService,
    private authService: AuthService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.getCurrentUser()?.username ?? null;
    this.loadMembers();
  }

  ngOnChanges(changes: SimpleChanges): void {
    // Reload members if the group id changed (e.g. user navigated to another group)
    if (changes['group'] && !changes['group'].firstChange) {
      const prev = changes['group'].previousValue as ChatGroupDto | null;
      const curr = changes['group'].currentValue as ChatGroupDto | null;
      if (curr && prev && curr.id !== prev.id) {
        this.loadMembers();
      }
    }
  }

  // ── Close context menu on click anywhere inside modal ────────────────────
  onModalClick(): void {
    this.openMenuForUser = null;
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.openMenuForUser) {
      this.openMenuForUser = null;
    } else {
      this.closeModal();
    }
  }

  // ── Members ───────────────────────────────────────────────────────────────

  loadMembers(): void {
    if (!this.group?.id) return;
    this.loading = true;
    this.chatService.getMembers(this.group.id).subscribe({
      next: (m) => {
        this.members = m.sort((a, b) => {
          if (a.username === this.group.createdByUsername) return -1;
          if (b.username === this.group.createdByUsername) return 1;
          if (a.role === 'ADMIN' && b.role !== 'ADMIN') return -1;
          if (b.role === 'ADMIN' && a.role !== 'ADMIN') return 1;
          return a.username.localeCompare(b.username);
        });
        // Determine current user's role from fresh member data (more reliable than group.currentUserRole)
        const me = this.members.find(x => x.username === this.currentUser);
        this.myRole = me?.role ?? null;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.notificationService.showToast('error', 'Error', 'No se pudieron cargar los participantes');
      }
    });
  }

  // ── Role helpers ──────────────────────────────────────────────────────────

  get isCreator(): boolean {
    return !!this.currentUser && this.currentUser === this.group?.createdByUsername;
  }

  get isAdmin(): boolean {
    // Prefer myRole (from freshly loaded member list) over group.currentUserRole (may be stale)
    const role = this.myRole ?? this.group?.currentUserRole;
    return role === 'ADMIN' || this.isCreator;
  }

  // ── Close ─────────────────────────────────────────────────────────────────

  closeModal(): void {
    this.close.emit();
  }

  // ── Edit group info ───────────────────────────────────────────────────────

  enableEditInfo(): void {
    if (!this.isAdmin) return;
    this.editName = this.group.name;
    this.editDescription = this.group.description || '';
    this.editInfoMode = true;
  }

  saveInfo(): void {
    if (!this.editName.trim()) return;
    this.actionLoading = true;
    this.chatService.updateGroupInfo(this.group.id, this.editName.trim(), this.editDescription.trim()).subscribe({
      next: () => {
        this.editInfoMode = false;
        this.actionLoading = false;
        this.notificationService.showToast('success', 'Grupo actualizado', 'Nombre y descripción guardados');
        this.groupUpdated.emit();
      },
      error: (err) => {
        this.actionLoading = false;
        const msg = err?.error?.message || err?.error?.error || 'Error al guardar los cambios';
        this.notificationService.showToast('error', 'Error', msg);
      }
    });
  }

  // ── Avatar ────────────────────────────────────────────────────────────────

  onImageSelected(e: Event): void {
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || !this.isAdmin) return;

    // Reset input immediately so the same file can be selected again if needed
    input.value = '';

    this.actionLoading = true;
    this.chatService.updateGroupAvatar(this.group.id, file).subscribe({
      next: (res: any) => {
        this.actionLoading = false;
        // Update local group reference immediately so the new avatar renders at once
        if (res?.imageFilename) {
          this.group = { ...this.group, imageFilename: res.imageFilename };
        }
        this.notificationService.showToast('success', 'Foto actualizada', 'La imagen del grupo se actualizó');
        this.groupUpdated.emit();
      },
      error: (err) => {
        this.actionLoading = false;
        const msg = err?.error?.message || err?.error?.error || 'Error al subir la imagen';
        this.notificationService.showToast('error', 'Error', msg);
      }
    });
  }

  /** Resolves a possibly-relative avatar URL to an absolute URL (needed in dev without proxy). */
  private resolveUrl(url: string | null): string | null {
    if (!url) return null;
    return url.startsWith('http') ? url : `${this.authService.BASE}${url}`;
  }

  getAvatarUrl(): string | null {
    if (!this.group?.imageFilename) return null;
    return this.resolveUrl(`/api/user/avatar/img/${this.group.imageFilename}`);
  }

  getInitials(): string {
    return (this.group?.name || '?').substring(0, 2).toUpperCase();
  }

  // ── Member display helpers ─────────────────────────────────────────────────

  getUserAvatar(m: MemberDto): string {
    return this.resolveUrl(m.avatarUrl ?? null) ?? '';
  }

  getUserInitials(username: string): string {
    return username.substring(0, 2).toUpperCase();
  }

  // ── Context menu ──────────────────────────────────────────────────────────

  toggleMenu(e: Event, username: string): void {
    e.stopPropagation();
    this.openMenuForUser = this.openMenuForUser === username ? null : username;
  }

  canManageUser(target: MemberDto): boolean {
    if (!this.currentUser) return false;
    if (target.username === this.currentUser) return false;
    if (this.isCreator) return true;
    // Admin can kick members but not the creator, not other admins
    if (this.isAdmin && target.username !== this.group.createdByUsername && target.role !== 'ADMIN') return true;
    return false;
  }

  // ── Promote / Demote ──────────────────────────────────────────────────────

  setRole(username: string, role: string): void {
    if (!this.isCreator) return;
    this.openMenuForUser = null;
    this.actionLoading = true;
    this.chatService.updateMemberRole(this.group.id, username, role).subscribe({
      next: () => {
        this.actionLoading = false;
        const label = role === 'ADMIN' ? 'Administrador asignado' : 'Rol eliminado';
        const detail = role === 'ADMIN' ? `${username} ahora es admin` : `${username} ya no es admin`;
        this.notificationService.showToast('success', label, detail);
        this.loadMembers();
        this.groupUpdated.emit();
      },
      error: (err) => {
        this.actionLoading = false;
        const msg = err?.error?.message || err?.error?.error || 'Error al cambiar el rol';
        this.notificationService.showToast('error', 'Error', msg);
      }
    });
  }

  // ── Kick ─────────────────────────────────────────────────────────────────

  kickUser(username: string): void {
    if (!this.isAdmin) return;
    this.openMenuForUser = null;
    if (!confirm(`¿Estás seguro de que deseas expulsar a ${username}?`)) return;

    this.actionLoading = true;
    this.chatService.kickMember(this.group.id, username).subscribe({
      next: () => {
        this.actionLoading = false;
        this.notificationService.showToast('success', 'Miembro expulsado', `${username} fue eliminado del grupo`);
        this.loadMembers();
        this.groupUpdated.emit();
      },
      error: (err) => {
        this.actionLoading = false;
        const msg = err?.error?.message || err?.error?.error || 'Error al expulsar al miembro';
        this.notificationService.showToast('error', 'Error', msg);
      }
    });
  }

  // ── Add member ────────────────────────────────────────────────────────────

  openAddMember(): void {
    this.addMemberMode = true;
    this.newMemberUsername = '';
  }

  cancelAddMember(): void {
    this.addMemberMode = false;
    this.newMemberUsername = '';
  }

  confirmAddMember(): void {
    const username = this.newMemberUsername.trim();
    if (!username) return;
    this.addMemberLoading = true;
    this.chatService.addMember(this.group.id, username).subscribe({
      next: () => {
        this.addMemberLoading = false;
        this.notificationService.showToast('success', 'Miembro añadido', `${username} se unió al grupo`);
        this.cancelAddMember();
        this.loadMembers();
        this.groupUpdated.emit();
      },
      error: (err) => {
        this.addMemberLoading = false;
        const msg = err?.error?.message || err?.error?.error || 'No se pudo añadir al usuario';
        this.notificationService.showToast('error', 'Error', msg);
      }
    });
  }

  // ── Leave / Delete ────────────────────────────────────────────────────────

  leaveGroup(): void {
    const msg = this.isCreator
      ? 'Eres el creador. Salir del grupo lo eliminará por completo. ¿Continuar?'
      : '¿Estás seguro de que deseas salir del grupo?';

    if (!confirm(msg)) return;

    this.chatService.leaveGroup(this.group.id).subscribe({
      next: () => {
        this.closeModal();
        this.groupUpdated.emit();
      },
      error: (err) => {
        const errMsg = err?.error?.message || err?.error?.error || 'Error al salir del grupo';
        this.notificationService.showToast('error', 'Error', errMsg);
      }
    });
  }
}