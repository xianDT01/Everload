import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FacebookDownloadsComponent } from './facebook-downloads.component';

describe('FacebookDownloadsComponent', () => {
  let component: FacebookDownloadsComponent;
  let fixture: ComponentFixture<FacebookDownloadsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ FacebookDownloadsComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(FacebookDownloadsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
