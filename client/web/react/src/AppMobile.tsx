import { useState, useRef, useEffect } from 'react'
import './App.css'
import { A2UIRenderer, type TimelineItem, themeStyleSheet } from '@googlemaps/a2ui/lit';
import { isAndroid, isIOS } from './utils/platform';

const PLACE_CARD_THUMBNAIL_MIN_WIDTH = 350;

/**
 * Mobile Web Application component that acts as a rendering engine for A2UI surfaces.
 * It receives JSON payloads from the native iOS/Android bridge and renders them.
 */
function AppMobile() {
  const [timeline, setTimeline] = useState<TimelineItem[]>([])
  const rendererRef = useRef(new A2UIRenderer())
  const globalDataModelRef = useRef<any>({})

  useEffect(() => {
    if (!document.adoptedStyleSheets.includes(themeStyleSheet)) {
      document.adoptedStyleSheets = [...document.adoptedStyleSheets, themeStyleSheet];
    }

    // --- Native Bridge Hack for A2UI-Shell compatibility ---
    const originalQuerySelector = document.querySelector.bind(document);
    document.querySelector = function<K extends keyof HTMLElementTagNameMap>(selectors: K): any {
        if (selectors as string === 'a2ui-shell') {
            return {
                processA2uiMessages: (json: string) => {
                    try {
                        let messages = typeof json === 'string' ? JSON.parse(json) : json;
                        if (typeof messages === 'string') {
                            messages = JSON.parse(messages);
                        }

                        if (!Array.isArray(messages)) {
                            messages = [messages];
                        }

                        // [A2UI Interceptor]
                        // 1. Auto-fix common LLM hallucinated keys ('latitude' -> 'lat', 'title' -> 'label').
                        function fixKeys(obj: any) {
                            if (Array.isArray(obj)) {
                                obj.forEach(fixKeys);
                            } else if (obj !== null && typeof obj === 'object') {
                                if (obj.latitude !== undefined) { obj.lat = obj.latitude; delete obj.latitude; }
                                if (obj.longitude !== undefined) { obj.lng = obj.longitude; delete obj.longitude; }
                                if (obj.title !== undefined && obj.label === undefined) { obj.label = obj.title; delete obj.title; }
                                Object.values(obj).forEach(fixKeys);
                            }
                        }
                        fixKeys(messages);

                        // 2. Track global data model and resolve 'path' references (e.g., Paris map bug).
                        // We must track the model globally because components and data often arrive in separate SSE chunks.
                        let hasUiInstructions = false;
                        const UI_KEYS = ['createSurface', 'updateComponents', 'updateDataModel', 'deleteSurface', 'beginRendering', 'surfaceUpdate'];
                        
                        messages.forEach((item: any) => {
                            // Check if this chunk actually contains UI instructions
                            if (UI_KEYS.some(key => item.hasOwnProperty(key))) {
                                hasUiInstructions = true;
                            }

                            if (item.updateDataModel && item.updateDataModel.value) {
                                globalDataModelRef.current = { ...globalDataModelRef.current, ...item.updateDataModel.value };
                            }
                        });

                        // 3. Deduplication: Only process this chunk in the WebView IF it contains actual UI instructions.
                        // If it's just pure conversational text, we ignore it here because Android's native bubble handles it.
                        if (!hasUiInstructions) {
                            return;
                        }
                        
                        function resolvePath(pathStr: string) {
                            if (!pathStr || !pathStr.startsWith('/')) return null;
                            let parts = pathStr.split('/').filter(Boolean);
                            let curr = globalDataModelRef.current;
                            for (let p of parts) {
                                if (curr && curr.hasOwnProperty(p)) curr = curr[p];
                                else return null;
                            }
                            return curr;
                        }

                        function fixGoogleMap(comp: any) {
                            if (comp.component === 'GoogleMap') {
                                if (comp.center && comp.center.path) {
                                    let resolved = resolvePath(comp.center.path);
                                    if (resolved) comp.center = resolved;
                                }
                            }
                            if (comp.children && Array.isArray(comp.children)) {
                                comp.children.forEach((c: any) => {
                                    if (typeof c === 'object') fixGoogleMap(c);
                                });
                            }
                        }

                        messages.forEach((item: any) => {
                            if (item.updateComponents && item.updateComponents.components) {
                                let comps = item.updateComponents.components;
                                comps.forEach(fixGoogleMap);
                                
                                // Ensure 'root' Column exists for the A2UI Renderer
                                let hasRoot = comps.some((c: any) => c.id === 'root');
                                if (!hasRoot && comps.length > 0) {
                                  // If no "root" exists, generating a new "root" container.
                                  let referencedChildIds = new Set<string>();
                                  comps.forEach((c: any) => {
                                    if (typeof c.child === 'string') referencedChildIds.add(c.child);
                                    if (c.children) {
                                      if (Array.isArray(c.children)) {
                                        c.children.forEach((child: any) => {
                                          if (typeof child === 'string') referencedChildIds.add(child);
                                          else if (child && typeof child === 'object' && child.id) referencedChildIds.add(child.id);
                                        });
                                      } else if (typeof c.children === 'object' && c.children.componentId) {
                                        referencedChildIds.add(c.children.componentId);
                                      }
                                    }
                                  });

                                  // Filter the components that are not claimed as a child by anyone
                                  let rootChildren = comps
                                    .filter((c: any) => c.id && !referencedChildIds.has(c.id))
                                    .map((c: any) => c.id);

                                    comps.unshift({
                                        id: 'root',
                                        component: 'Column',
                                        children: rootChildren
                                    });
                                }
                            }
                        });

                        rendererRef.current.processResponse(messages.map((msg: any) => ({ type: "a2ui", message: msg })));
                        setTimeline([...rendererRef.current.timeline]);
                    } catch (e) {
                        console.error("Failed to process A2UI JSON:", e);
                    }
                }
            };
        }
        return originalQuerySelector(selectors);
    };

    // Resize logic for native platforms
    const rootElement = document.getElementById('root');
    if (rootElement) {
        rootElement.style.height = 'auto';
        rootElement.style.minHeight = '0';
    }

    let timeoutId: any = null;
    const resizeObserver = new ResizeObserver(entries => {
        clearTimeout(timeoutId);
        timeoutId = setTimeout(() => {
            for (let entry of entries) {
                const newHeight = entry.target.scrollHeight;
                if (isAndroid && typeof (window as any).Android?.onWebpageResized === 'function') {
                    (window as any).Android.onWebpageResized(newHeight);
                } else if (isIOS && (window as any).webkit?.messageHandlers?.heightObserver) {
                    (window as any).webkit.messageHandlers.heightObserver.postMessage(newHeight);
                }
            }
        }, 100);
    });

    const container = document.querySelector('.mobile-app-container') || document.body;
    if (container) {
        resizeObserver.observe(container);
    }

    if (isAndroid && typeof (window as any).Android?.onJsReady === 'function') {
        (window as any).Android.onJsReady();
    } else if (isIOS) {
        (window as any).webkit?.messageHandlers?.iOS?.postMessage({ action: 'onJsReady', data: '' });
    }

    if (isIOS) {
      customElements.whenDefined('a2ui-placecard').then(() => {
        const PlaceCard = customElements.get('a2ui-placecard');
        if (!PlaceCard) return;
        const orig = PlaceCard.prototype.firstUpdated;

        PlaceCard.prototype.firstUpdated = function (changedProperties: any) {
          if (orig) orig.call(this, changedProperties);

          const compact = this.shadowRoot?.querySelector('gmp-place-details-compact') as HTMLElement | null;
          if (!compact) return;

          new ResizeObserver(() => {
            const parentWidth = this.clientWidth;

            // The Maps SDK `<gmp-place-details-compact>` hides thumbnails if its container is < 350px.
            // On standard iOS devices, the chat padding reduces the bubble width below 350px.
            const targetWidth = PLACE_CARD_THUMBNAIL_MIN_WIDTH;

            if (parentWidth > 0 && parentWidth < targetWidth) {
              // 1. Force the component to be exactly 350px so the Maps SDK renders the thumbnail image.
              compact.style.setProperty('width', targetWidth + 'px', 'important');
              compact.style.setProperty('min-width', targetWidth + 'px', 'important');

              // 2. Visually shrink the 350px component down to fit inside the physical chat bubble width.
              const scale = parentWidth / targetWidth;
              compact.style.setProperty('transform-origin', 'top left', 'important');
              compact.style.setProperty('transform', `scale(${scale})`, 'important');

              // 3. Since `transform: scale()` only changes visual size (not the DOM layout footprint), 
              // the parent container still thinks the component is its original full height.
              // We apply a negative bottom margin to crop out the empty dead space.
              const height = compact.offsetHeight;
              if (height > 0) {
                compact.style.setProperty('margin-bottom', `-${height * (1 - scale)}px`, 'important');
              }
            } else {
              // 4. If the screen is wide enough (e.g. device rotated to landscape mode), 
              // strip away all hacks and let the component render natively.
              compact.style.removeProperty('width');
              compact.style.removeProperty('min-width');
              compact.style.removeProperty('transform');
              compact.style.removeProperty('margin-bottom');
            }
          }).observe(this);
        };
      });
    }

    return () => {
        resizeObserver.disconnect();
        if (timeoutId) {
            clearTimeout(timeoutId);
        }
    };
  }, []);

  return (
    <div className="mobile-app-container" style={{ width: '100%', height: 'auto', display: 'flex', flexDirection: 'column', overflow: 'visible' }}>
      <div className="chat-messages" style={{ height: 'auto', padding: '16px', background: 'var(--social-bg)', overflow: 'visible' }}>
        <maui-providers>
          {timeline.length === 0 && (
            <p style={{ opacity: 0.5, textAlign: 'center', marginTop: '20px' }}>Waiting for payload...</p>
          )}
          {timeline.map((item) => {
            if (item.type === 'surface') {
              const surface = rendererRef.current.getSurface(item.surfaceId)
              if (!surface) return null
              return (
                <div key={item.surfaceId} className="surface-message">
                  {/* @ts-ignore */}
                  <a2ui-surface surface={surface}></a2ui-surface>
                </div>
              )
            }
            return null
          })}
        </maui-providers>
      </div>
    </div>
  )
}

export default AppMobile
