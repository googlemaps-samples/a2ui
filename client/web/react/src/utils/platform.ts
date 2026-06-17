/*
 Copyright 2026 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

export const isAndroid = typeof window !== 'undefined' && typeof (window as any).Android !== 'undefined';
export const isIOS = typeof window !== 'undefined' && typeof (window as any).webkit?.messageHandlers?.iOS !== 'undefined';
export const isMobileWebView = isAndroid || isIOS;

export const getAttributionId = () => {
  if (isAndroid) return "gmp_web_maui_v0.1.7_exp,gmp_android_maui_v0.1.7_exp";
  if (isIOS) return "gmp_web_maui_v0.1.7_exp,gmp_ios_maui_v0.1.7_exp";
  return "gmp_web_maui_v0.1.7_exp";
};
