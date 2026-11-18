import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';
import { SetupComponent } from './setup.component';
import { SetupService } from '../../services/setup.service';
import { AuthService } from '../../services/auth.service';

describe('First-run wizard', () => {
  let fixture: ComponentFixture<SetupComponent>;
  let component: SetupComponent;
  let api: any, auth: any, router: any;
  const config = { libraries: [], services: { spotify: false, youtube: false, acoustid: false } };
  const inaccessible = { root: '/app/nas_storage', relativePath: '', readable: false, writable: false, folders: [] };

  beforeEach(async () => {
    sessionStorage.removeItem('everload_setup_step');
    api = {
      status: jasmine.createSpy().and.returnValue(of({ required: true })),
      configuration: jasmine.createSpy().and.returnValue(of(config)),
      storage: jasmine.createSpy().and.returnValue(of(inaccessible)),
      library: jasmine.createSpy().and.returnValue(of({ id: 1, name: 'Music', readable: true, writable: false })),
      save: jasmine.createSpy().and.returnValue(of({ status: 'saved' })),
      verify: jasmine.createSpy().and.returnValue(of({ status: 'verified' }))
    };
    auth = {
      currentUser$: new BehaviorSubject(null), isLoggedIn: jasmine.createSpy().and.returnValue(false),
      isAdmin: jasmine.createSpy().and.returnValue(false), getCurrentUser: () => ({ username: 'owner' }),
      createAdministrator: jasmine.createSpy().and.returnValue(of({ username: 'owner', role: 'ADMIN' }))
    };
    router = { navigateByUrl: jasmine.createSpy().and.returnValue(Promise.resolve(true)) };
    await TestBed.configureTestingModule({
      imports: [SetupComponent, TranslateModule.forRoot()],
      providers: [{ provide: SetupService, useValue: api }, { provide: AuthService, useValue: auth }, { provide: Router, useValue: router }]
    }).compileComponents();
    fixture = TestBed.createComponent(SetupComponent); component = fixture.componentInstance;
  });
  async function start() { fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges(); }
  function validForm() { component.form.setValue({ code: 'one-use-code', username: 'owner', email: 'owner@example.com', password: 'valid-password-123', confirmation: 'valid-password-123' }); }
  afterEach(() => { sessionStorage.removeItem('everload_setup_step'); });

  it('starts with no prefilled administrator credentials', async () => {
    await start(); expect(component.step).toBe(1); expect(component.form.controls.password.value).toBe('');
    component.start(); fixture.detectChanges(); expect(fixture.nativeElement.querySelector('#setup-code')).toBeTruthy();
  });
  it('does not send mismatching passwords', async () => {
    await start(); validForm(); component.form.controls.confirmation.setValue('not-the-same'); await component.createAdmin();
    expect(auth.createAdministrator).not.toHaveBeenCalled(); expect(component.fieldError('confirmation')).toBeTrue();
  });
  it('requires a valid email before creating the account', async () => {
    await start(); validForm();
    for (const email of ['', 'invalid-address']) {
      component.form.controls.email.setValue(email); await component.createAdmin();
      expect(component.fieldError('email')).toBeTrue();
    }
    expect(auth.createAdministrator).not.toHaveBeenCalled();
  });
  it('keeps registration open when the email is already in use', async () => {
    await start(); validForm();
    auth.createAdministrator.and.returnValue(throwError(() => ({ status: 409, error: { error: 'SETUP_EMAIL' } })));
    await component.createAdmin(); expect(component.closed).toBeFalse(); expect(component.error).toBe('SETUP_EMAIL');
  });
  it('opens code instructions without submitting or clearing the form', async () => {
    await start(); component.start(); validForm(); fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('[aria-controls="code-help"]') as HTMLButtonElement;
    expect(button.getAttribute('aria-expanded')).toBe('false'); button.click(); fixture.detectChanges();
    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(fixture.nativeElement.querySelector('#code-help').hidden).toBeFalse();
    expect(fixture.nativeElement.querySelector('#code-help').textContent).toContain('docker compose exec');
    expect(component.form.controls.email.value).toBe('owner@example.com');
    expect(auth.createAdministrator).not.toHaveBeenCalled();
  });
  it('provides independent help and safe official links for all integrations', async () => {
    await start(); component.go(4); fixture.detectChanges();
    for (const service of component.services) {
      const button = fixture.nativeElement.querySelector(`[aria-controls="service-help-${service.id}"]`) as HTMLButtonElement;
      button.click(); fixture.detectChanges();
      const help = fixture.nativeElement.querySelector(`#service-help-${service.id}`) as HTMLElement;
      expect(help.hidden).toBeFalse(); expect(help.querySelectorAll('li').length).toBe(3);
      const links = help.querySelectorAll('a'); expect(links.length).toBe(2);
      links.forEach(link => { expect(link.href).toMatch(/^https:\/\//); expect(link.rel).toContain('noopener'); });
      button.click(); fixture.detectChanges(); expect(help.hidden).toBeTrue();
    }
    expect(api.save).not.toHaveBeenCalled(); expect(api.verify).not.toHaveBeenCalled();
  });
  it('creates the account and proceeds even with no NAS mounted', async () => {
    await start(); validForm(); await component.createAdmin(); fixture.detectChanges();
    expect(component.step).toBe(3); expect(component.storage?.readable).toBeFalse();
    expect(component.form.controls.code.value).toBe(''); expect(component.form.controls.password.value).toBe('');
    expect(fixture.nativeElement.querySelector('.access-state .error')).toBeTruthy();
  });
  it('can finish with neither a library nor API keys', async () => {
    await start(); component.go(4); await component.summary();
    auth.isLoggedIn.and.returnValue(true); component.enter();
    expect(api.library).not.toHaveBeenCalled(); expect(api.save).not.toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
  });
  it('never offers EverWave and opens the Home even with an accessible library', async () => {
    await start(); component.libraries = [{ id: 1, name: 'Music', path: '/app/nas_storage', readable: true, writable: true }];
    component.go(5); fixture.detectChanges(); auth.isLoggedIn.and.returnValue(true); component.enter();
    expect(fixture.nativeElement.textContent).not.toContain('EverWave');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
  });
  it('cannot go back to first-admin creation after success', async () => {
    await start(); component.go(3); component.go(2); expect(component.step).toBe(3);
  });
  it('closes an already claimed installation in a second tab', async () => {
    api.status.and.returnValue(of({ required: false })); await start(); expect(component.closed).toBeTrue();
    expect(fixture.nativeElement.querySelector('#setup-code')).toBeNull();
  });
  it('resumes optional steps on reload only for the signed-in admin', async () => {
    sessionStorage.setItem('everload_setup_step', '3');
    api.status.and.returnValue(of({ required: false })); auth.isLoggedIn.and.returnValue(true); auth.isAdmin.and.returnValue(true);
    await start(); expect(component.step).toBe(3); expect(component.closed).toBeFalse();
  });
  it('requires login instead of reopening registration when the session is lost', async () => {
    await start(); component.go(3); auth.currentUser$.next(null); fixture.detectChanges();
    expect(component.closed).toBeTrue(); component.enter(); expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });
  it('handles another client having claimed the installation before submit', async () => {
    await start(); validForm(); auth.createAdministrator.and.returnValue(throwError(() => ({ status: 409, error: { error: 'SETUP_CLOSED' } })));
    await component.createAdmin(); expect(component.closed).toBeTrue();
  });
  it('ignores duplicate clicks while account creation is pending', async () => {
    await start(); validForm(); const pending = new Subject<any>(); auth.createAdministrator.and.returnValue(pending);
    const first = component.createAdmin(); await component.createAdmin(); expect(auth.createAdministrator).toHaveBeenCalledTimes(1);
    pending.next({}); pending.complete(); await first;
  });
  it('does not expose an editable read-only checkbox for a locked mount', async () => {
    await start(); component.go(3); component.storage = { ...inaccessible, readable: true };
    fixture.detectChanges(); expect(fixture.nativeElement.querySelector('input[type=checkbox]')).toBeNull();
    expect(fixture.nativeElement.querySelector('.access-state .success')).toBeTruthy();
  });
  it('reports saving separately from credential verification', async () => {
    await start(); const service = component.services[0]; service.fields.forEach(field => field.value = 'test-only');
    api.verify.and.returnValue(of({ status: 'failed' })); await component.saveService(service, true);
    expect(service.configured).toBeTrue(); expect(service.state).toBe('failed'); expect(service.fields.every(field => !field.value)).toBeTrue();
    await component.finishServices(); expect(component.step).toBe(5);
  });
  it('does not pretend AcoustID has an isolated key verification', async () => {
    await start(); const service = component.services[2]; service.fields[0].value = 'test-only';
    await component.saveService(service, true); expect(api.verify).not.toHaveBeenCalled(); expect(service.state).toBe('saved');
  });
  it('moves focus to the heading of each new step', fakeAsync(() => {
    fixture.detectChanges(); tick(); fixture.detectChanges();
    component.start(); fixture.detectChanges(); tick();
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('.setup-card h1'));
  }));
});
