import { ComponentFixture, TestBed } from '@angular/core/testing';

import { YoutubeDownloadsComponent } from './youtube-downloads.component';

describe('YoutubeDownloadsComponent', () => {
  let component: YoutubeDownloadsComponent;
  let fixture: ComponentFixture<YoutubeDownloadsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ YoutubeDownloadsComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(YoutubeDownloadsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
