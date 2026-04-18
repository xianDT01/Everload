import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Routes } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';

import { AdminConfigComponent } from './admin-config.component';

const routes: Routes = [
  { path: '', component: AdminConfigComponent },
];

@NgModule({
  declarations: [AdminConfigComponent],
  imports: [
    CommonModule,
    FormsModule,
    HttpClientModule,
    TranslateModule,
    RouterModule.forChild(routes),
  ],
})
export class AdminModule {}