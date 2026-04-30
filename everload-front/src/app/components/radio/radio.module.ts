import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { RouterModule, Routes } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

import { RadioComponent } from './radio.component';

const routes: Routes = [
  { path: '', component: RadioComponent },
];

@NgModule({
  declarations: [RadioComponent],
  imports: [
    CommonModule,
    FormsModule,
    HttpClientModule,
    TranslateModule,
    RouterModule.forChild(routes),
  ],
})
export class RadioModule {}
