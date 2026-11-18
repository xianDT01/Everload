import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { LoginComponent } from './login.component';
import { RegisterComponent } from '../register/register.component';

for (const Component of [LoginComponent, RegisterComponent]) {
  describe(Component.name + ' blank hero', () => {
    let component: LoginComponent | RegisterComponent;
    let http: HttpTestingController;
    beforeEach(() => {
      TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
      http = TestBed.inject(HttpTestingController);
      component = new Component({ isLoggedIn: () => false } as any, {} as any, {} as any,
        TestBed.inject(HttpClient), { backendUrl: '' } as any);
    });
    afterEach(() => http.verify());
    it('has no initial artist images and stays blank with an empty response', () => {
      expect(component.artistHeroImages).toEqual(Array(6).fill('none'));
      component.ngOnInit(); http.expectOne('/api/public/auth-hero-images').flush({ images: [] });
      expect(component.artistHeroImages).toEqual(Array(6).fill('none'));
    });
    it('never restores sample artists on API failure', () => {
      component.ngOnInit(); http.expectOne('/api/public/auth-hero-images').flush({}, { status: 500, statusText: 'Error' });
      expect(component.artistHeroImages).toEqual(Array(6).fill('none'));
    });
    it('renders only configured images without filling the other slots', () => {
      component.ngOnInit(); http.expectOne('/api/public/auth-hero-images').flush({ images: ['', '', '/own-image.jpg'] });
      expect(component.artistHeroImages).toEqual(['none', 'none', 'url("/own-image.jpg")', 'none', 'none', 'none']);
    });
  });
}
