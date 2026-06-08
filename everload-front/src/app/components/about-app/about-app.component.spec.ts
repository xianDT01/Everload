import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { of } from 'rxjs';

import { AboutAppComponent } from './about-app.component';
import { TranslateService } from '@ngx-translate/core';

/** Stand-in for the real `translate` pipe so the template can render without ngx-translate's loader machinery. */
@Pipe({ name: 'translate' })
class FakeTranslatePipe implements PipeTransform {
  transform(value: string): string { return value; }
}

describe('AboutAppComponent', () => {
  let component: AboutAppComponent;
  let fixture: ComponentFixture<AboutAppComponent>;
  let translateSpy: jasmine.SpyObj<TranslateService>;

  beforeEach(async () => {
    translateSpy = jasmine.createSpyObj<TranslateService>(
      'TranslateService',
      ['addLangs', 'setDefaultLang', 'getBrowserCultureLang', 'use', 'instant']
    );
    translateSpy.getBrowserCultureLang.and.returnValue('es-ES');
    translateSpy.use.and.returnValue(of({}));
    translateSpy.instant.and.callFake((key: string) => key);

    await TestBed.configureTestingModule({
      declarations: [AboutAppComponent, FakeTranslatePipe],
      providers: [{ provide: TranslateService, useValue: translateSpy }],
    })
    .compileComponents();

    fixture = TestBed.createComponent(AboutAppComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
