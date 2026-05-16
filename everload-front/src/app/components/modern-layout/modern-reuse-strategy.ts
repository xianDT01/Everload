import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, DetachedRouteHandle, RouteReuseStrategy } from '@angular/router';

/**
 * Keeps modern layout child pages alive in memory so navigating between
 * Library → Albums → Library skips destroy/create and re-render entirely.
 * Only affects leaf routes whose URL starts with "modern/".
 */
@Injectable({ providedIn: 'root' })
export class ModernReuseStrategy implements RouteReuseStrategy {
  private stored = new Map<string, DetachedRouteHandle>();

  private key(route: ActivatedRouteSnapshot): string {
    const segments = route.pathFromRoot.flatMap(r => r.url).map(s => s.path);
    return segments.join('/') || 'modern/home';
  }

  private isModernLeaf(route: ActivatedRouteSnapshot): boolean {
    return !route.children.length && this.key(route).startsWith('modern');
  }

  shouldDetach(route: ActivatedRouteSnapshot): boolean {
    return this.isModernLeaf(route);
  }

  store(route: ActivatedRouteSnapshot, handle: DetachedRouteHandle | null): void {
    if (handle) this.stored.set(this.key(route), handle);
  }

  shouldAttach(route: ActivatedRouteSnapshot): boolean {
    return this.isModernLeaf(route) && this.stored.has(this.key(route));
  }

  retrieve(route: ActivatedRouteSnapshot): DetachedRouteHandle | null {
    return this.stored.get(this.key(route)) ?? null;
  }

  shouldReuseRoute(future: ActivatedRouteSnapshot, curr: ActivatedRouteSnapshot): boolean {
    return future.routeConfig === curr.routeConfig;
  }
}
