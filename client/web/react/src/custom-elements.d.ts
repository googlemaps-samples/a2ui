import * as React from 'react';

declare module 'react' {
  namespace JSX {
    interface IntrinsicElements {
      'a2ui-surface': any;
      'a2ui-theme-provider': any;
      'maui-providers': any;
    }
  }
}