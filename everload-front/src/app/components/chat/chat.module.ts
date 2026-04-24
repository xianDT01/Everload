import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Routes } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';

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
    TranslateModule,
    RouterModule.forChild(routes),
  ],
  exports: [
    ChatComponent,
  ]
})
export class ChatModule {}
