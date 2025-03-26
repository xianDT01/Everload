import { ComponentFixture, TestBed } from '@angular/core/testing';

import { InstagramDownloadsComponent } from './instagram-downloads.component';

describe('InstagramDownloadsComponent', () => {
  let component: InstagramDownloadsComponent;
  let fixture: ComponentFixture<InstagramDownloadsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ InstagramDownloadsComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(InstagramDownloadsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
