/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

import { LAppDelegate } from './lappdelegate';
import * as LAppDefine from './lappdefine';

const mindIsleParams = new URLSearchParams(window.location.search);
const mindIsleModelPath =
  mindIsleParams.get('model') ||
  '../xingyue_shuimu_safe/xingyue_shuimu_safe.model3.json';
const mindIsleModelId = mindIsleParams.get('modelId') || 'unknown';

console.log('[Live2DViewer5] start');
console.log('[Live2DViewer5] modelId =', mindIsleModelId);
console.log('[Live2DViewer5] modelPath =', mindIsleModelPath);

function reportViewer5Error(error: unknown): void {
  const errorObject = error as Error | undefined;
  console.error('[Live2DViewer5] load failed');
  console.error('[Live2DViewer5] error name =', errorObject?.name);
  console.error(
    '[Live2DViewer5] error message =',
    errorObject?.message ?? String(error)
  );
  console.error('[Live2DViewer5] error stack =', errorObject?.stack);

  try {
    (
      window as unknown as {
        AndroidLive2D?: { onModelLoadFailed?: (message: string) => void };
      }
    ).AndroidLive2D?.onModelLoadFailed?.(
      '星月水母模型加载失败，请检查 Cubism 5 运行库或模型资源。'
    );
  } catch {
    // Android bridge is optional in browser-side tests.
  }
}

window.addEventListener('error', (event: ErrorEvent): void => {
  reportViewer5Error(event.error);
});

window.addEventListener('unhandledrejection', (event: PromiseRejectionEvent): void => {
  reportViewer5Error(event.reason);
});

(window as unknown as {
  MindIsleViewer5ModelPath?: string;
  MindIsleViewer5ModelId?: string;
}).MindIsleViewer5ModelPath = mindIsleModelPath;
(window as unknown as {
  MindIsleViewer5ModelPath?: string;
  MindIsleViewer5ModelId?: string;
}).MindIsleViewer5ModelId = mindIsleModelId;

type MindIsleViewer5Api = {
  applyExpressionByFile: (expressionFile: string) => void;
  lookAtSmooth: (x: number, y: number) => void;
  playMotionByFile: (motionFile: string) => void;
  playTapReactionSmooth: (x: number, y: number) => void;
  resetLookAtSmooth: () => void;
  setDisplayPreset: (preset: string) => void;
  setTalkState: (talking: boolean) => void;
};

const mindIsleViewer5Api: MindIsleViewer5Api = {
  applyExpressionByFile: (expressionFile: string): void => {
    return LAppDelegate.getInstance().applyExpressionByFile(expressionFile);
  },
  lookAtSmooth: (x: number, y: number): void => {
    return LAppDelegate.getInstance().lookAtSmooth(x, y);
  },
  playMotionByFile: (motionFile: string): void => {
    return LAppDelegate.getInstance().playMotionByFile(motionFile);
  },
  playTapReactionSmooth: (x: number, y: number): void => {
    return LAppDelegate.getInstance().playTapReactionSmooth(x, y);
  },
  resetLookAtSmooth: (): void => {
    return LAppDelegate.getInstance().resetLookAtSmooth();
  },
  setDisplayPreset: (preset: string): void => {
    return LAppDelegate.getInstance().setDisplayPreset(preset);
  },
  setTalkState: (talking: boolean): void => {
    return LAppDelegate.getInstance().setTalkState(talking);
  }
};

(window as unknown as { MindIsleViewer5?: MindIsleViewer5Api }).MindIsleViewer5 =
  mindIsleViewer5Api;

(window as unknown as {
  setDisplayPreset?: (preset: string) => void;
}).setDisplayPreset = (preset: string): void => {
  console.log('[Live2DViewer5] setDisplayPreset called', preset);
  return mindIsleViewer5Api.setDisplayPreset(preset);
};

(window as unknown as {
  applyExpressionByFile?: (expressionFile: string) => void;
}).applyExpressionByFile = (expressionFile: string): void => {
  console.log('[Live2DViewer5] applyExpressionByFile called', expressionFile);
  return mindIsleViewer5Api.applyExpressionByFile(expressionFile);
};

(window as unknown as {
  playMotionByFile?: (motionFile: string) => void;
}).playMotionByFile = (motionFile: string): void => {
  console.log('[Live2DViewer5] playMotionByFile called', motionFile);
  return mindIsleViewer5Api.playMotionByFile(motionFile);
};

(window as unknown as {
  lookAtSmooth?: (x: number, y: number, source?: string) => void;
}).lookAtSmooth = (x: number, y: number, source?: string): void => {
  console.log('[Live2DViewer5] look target', x, y, source ?? 'direct');
  return mindIsleViewer5Api.lookAtSmooth(x, y);
};

(window as unknown as {
  playTapReactionSmooth?: (x: number, y: number, source?: string) => void;
}).playTapReactionSmooth = (x: number, y: number, source?: string): void => {
  console.log('[Live2DViewer5] tap reaction', x, y, source ?? 'direct');
  return mindIsleViewer5Api.playTapReactionSmooth(x, y);
};

(window as unknown as {
  resetLookAtSmooth?: () => void;
}).resetLookAtSmooth = (): void => {
  return mindIsleViewer5Api.resetLookAtSmooth();
};

(window as unknown as {
  setTalkState?: (talking: boolean) => void;
}).setTalkState = (talking: boolean): void => {
  console.log('[Live2DViewer5] talk state requested', talking);
  return mindIsleViewer5Api.setTalkState(talking);
};

/**
 * ブラウザロード後の処理
 */
window.addEventListener(
  'load',
  (): void => {
    // Initialize WebGL and create the application instance
    if (!LAppDelegate.getInstance().initialize()) {
      return;
    }

    LAppDelegate.getInstance().run();
  },
  { passive: true }
);

/**
 * 終了時の処理
 */
window.addEventListener(
  'beforeunload',
  (): void => LAppDelegate.releaseInstance(),
  { passive: true }
);
