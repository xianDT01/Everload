import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Routes } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { MatIconModule } from '@angular/material/icon';
import { A11yModule } from '@angular/cdk/a11y';

import { ChatComponent } from './chat.component';
import { CreateGroupModalComponent } from './create-group-modal/create-group-modal.component';
import { GroupInfoModalComponent } from './group-info-modal/group-info-modal.component';
import { MentionHighlightPipe } from '../../pipes/mention-highlight.pipe';

const routes: Routes = [
  { path: '', component: ChatComponent },
];

@NgModule({
  declarations: [
    ChatComponent,
    CreateGroupModalComponent,
    GroupInfoModalComponent,
    MentionHighlightPipe,
  ],
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
    A11yModule,
    TranslateModule,
    RouterModule.forChild(routes),
  ],
  exports: [
    ChatComponent,
  ]
})
export class ChatModule {}
