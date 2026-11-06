import { TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { A11yModule } from '@angular/cdk/a11y';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { of, Subject } from 'rxjs';
import { CreateGroupModalComponent } from './create-group-modal/create-group-modal.component';
import { GroupInfoModalComponent } from './group-info-modal/group-info-modal.component';
import { ChatService, ChatGroupDto } from '../../services/chat.service';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';

describe('Chat dialog appearance and controls', () => {
  let service: jasmine.SpyObj<ChatService>;
  const group = { id: 5, name: 'Music', type: 'GROUP', createdByUsername: 'Demo', memberCount: 2 } as ChatGroupDto;

  beforeEach(async () => {
    service = jasmine.createSpyObj('ChatService', ['getActiveUsers', 'createGroup', 'getMembers']);
    service.getActiveUsers.and.returnValue(of([{ username: 'Adrian', online: true } as any]));
    service.createGroup.and.returnValue(of(group));
    service.getMembers.and.returnValue(of([{ username: 'Demo', role: 'ADMIN' }, { username: 'Adrian', role: 'MEMBER' }] as any));
    await TestBed.configureTestingModule({
      declarations: [CreateGroupModalComponent, GroupInfoModalComponent],
      imports: [CommonModule, FormsModule, A11yModule, MatIconModule, TranslateModule.forRoot()],
      providers: [
        { provide: ChatService, useValue: service },
        { provide: AuthService, useValue: { getCurrentUser: () => ({ username: 'Demo' }), BASE: '' } },
        { provide: NotificationService, useValue: { showToast: jasmine.createSpy() } }
      ]
    }).compileComponents();
  });

  it('uses graphite surfaces and a mint action in the standalone group dialog', async () => {
    const fixture = TestBed.createComponent(CreateGroupModalComponent);
    fixture.componentInstance.modern = true;
    fixture.componentInstance.name = 'Music';
    fixture.detectChanges();
    await fixture.whenStable();
    expect(getComputedStyle(fixture.nativeElement.querySelector('.modal-box')).backgroundColor).toBe('rgb(32, 34, 36)');
    const checkbox = fixture.nativeElement.querySelector('input[type="checkbox"]') as HTMLInputElement;
    (checkbox.closest('.user-item') as HTMLElement).style.transition = 'none';
    checkbox.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.selectedUsernames.has('Adrian')).toBeTrue();
    expect(getComputedStyle(fixture.nativeElement.querySelector('.user-item')).display).toBe('flex');
    expect(getComputedStyle(fixture.nativeElement.querySelector('.user-list')).backgroundColor).toBe('rgb(32, 34, 36)');
    expect(getComputedStyle(fixture.nativeElement.querySelector('.user-item.selected')).backgroundColor).toBe('rgb(51, 55, 59)');
    expect(getComputedStyle(checkbox).accentColor).toBe('rgb(107, 213, 200)');
    expect(fixture.nativeElement.querySelector('.btn-create').disabled).toBeFalse();
    expect(getComputedStyle(fixture.nativeElement.querySelector('.btn-create')).backgroundColor).toBe('rgb(107, 213, 200)');
    expect(fixture.nativeElement.querySelector('[role="dialog"]').getAttribute('aria-modal')).toBe('true');
    fixture.destroy();
  });

  it('preserves the embedded dialog palette instead of applying the standalone skin', async () => {
    const fixture = TestBed.createComponent(CreateGroupModalComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.chat-dialog')).toBeNull();
    expect(getComputedStyle(fixture.nativeElement.querySelector('.modal-box')).backgroundColor).toBe('rgb(22, 33, 62)');
    fixture.destroy();
  });

  it('uses the same surface for group information and closes with Escape', async () => {
    const fixture = TestBed.createComponent(GroupInfoModalComponent);
    fixture.componentInstance.modern = true;
    fixture.componentInstance.group = group;
    fixture.detectChanges();
    await fixture.whenStable();
    expect(getComputedStyle(fixture.nativeElement.querySelector('.modal-box')).backgroundColor).toBe('rgb(32, 34, 36)');
    expect(fixture.nativeElement.querySelector('.group-info-body')).not.toBeNull();
    const close = spyOn(fixture.componentInstance.close, 'emit');
    fixture.componentInstance.onEscape();
    expect(close).toHaveBeenCalledTimes(1);
    fixture.destroy();
  });

  it('submits the selected members once while a request is pending', () => {
    const pending = new Subject<ChatGroupDto>();
    service.createGroup.and.returnValue(pending);
    const fixture = TestBed.createComponent(CreateGroupModalComponent);
    const component = fixture.componentInstance;
    component.name = ' Music '; component.toggleUser('Adrian');
    component.submit(); component.submit();
    expect(service.createGroup).toHaveBeenCalledOnceWith({ name: 'Music', description: undefined, type: 'GROUP', memberUsernames: ['Adrian'] });
    pending.next(group);
    expect(component.isLoading).toBeFalse();
    pending.complete(); fixture.destroy();
  });
});
