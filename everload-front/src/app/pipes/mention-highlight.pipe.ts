import { Pipe, PipeTransform } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

@Pipe({ name: 'mentionHighlight' })
export class MentionHighlightPipe implements PipeTransform {
  constructor(private sanitizer: DomSanitizer) {}

  transform(content: string, currentUser = ''): SafeHtml {
    const escaped = content
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
    const withMentions = escaped.replace(/@(\w+)/g, (_, username: string) => {
      const cls = username === currentUser ? 'mention mention-self' : 'mention';
      return `<span class="${cls}">@${username}</span>`;
    });
    return this.sanitizer.bypassSecurityTrustHtml(withMentions);
  }
}
