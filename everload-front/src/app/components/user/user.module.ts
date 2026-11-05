import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Routes } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { MatIconModule } from '@angular/material/icon';

import { UserProfileComponent } from '../user-profile/user-profile.component';
import { AboutAppComponent } from '../about-app/about-app.component';

const routes: Routes = [
  { path: 'profile',   component: UserProfileComponent },
  { path: 'about-app', component: AboutAppComponent },
];

@NgModule({
  declarations: [
    UserProfileComponent,
    AboutAppComponent,
  ],
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
    HttpClientModule,
    TranslateModule,
    RouterModule.forChild(routes),
  ],
})
export class UserModule {}
