import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TwitterDownloadsComponent } from './twitter-downloads.component';

describe('TwitterDownloadsComponent', () => {
  let component: TwitterDownloadsComponent;
  let fixture: ComponentFixture<TwitterDownloadsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ TwitterDownloadsComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TwitterDownloadsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
