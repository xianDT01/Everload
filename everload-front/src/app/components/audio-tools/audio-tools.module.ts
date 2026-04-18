import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Routes } from '@angular/router';
import { HttpClientModule } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';

import { AudioToolsComponent } from './audio-tools.component';

const routes: Routes = [
  { path: '', component: AudioToolsComponent },
];

@NgModule({
  declarations: [AudioToolsComponent],
  imports: [
    CommonModule,
    HttpClientModule,
    TranslateModule,
    RouterModule.forChild(routes),
  ],
})
export class AudioToolsModule {}