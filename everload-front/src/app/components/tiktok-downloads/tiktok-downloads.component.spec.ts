import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TiktokDownloadsComponent } from './tiktok-downloads.component';

describe('TiktokDownloadsComponent', () => {
  let component: TiktokDownloadsComponent;
  let fixture: ComponentFixture<TiktokDownloadsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ TiktokDownloadsComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TiktokDownloadsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
