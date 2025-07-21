import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SpotifyDownloadsComponent } from './spotify-downloads.component';

describe('SpotifyDownloadsComponent', () => {
  let component: SpotifyDownloadsComponent;
  let fixture: ComponentFixture<SpotifyDownloadsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ SpotifyDownloadsComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SpotifyDownloadsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
