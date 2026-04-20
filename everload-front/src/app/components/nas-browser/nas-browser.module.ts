import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { NasBrowserComponent } from './nas-browser.component';

@NgModule({
  declarations: [NasBrowserComponent],
  imports: [CommonModule, FormsModule, TranslateModule],
  exports: [NasBrowserComponent],
})
export class NasBrowserModule {}
