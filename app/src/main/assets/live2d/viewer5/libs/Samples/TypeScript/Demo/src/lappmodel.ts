/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

import { CubismDefaultParameterId } from '@framework/cubismdefaultparameterid';
import { CubismModelSettingJson } from '@framework/cubismmodelsettingjson';
import {
  BreathParameterData,
  CubismBreath
} from '@framework/effect/cubismbreath';
import { LookParameterData, CubismLook } from '@framework/effect/cubismlook';
import { CubismEyeBlink } from '@framework/effect/cubismeyeblink';
import { ICubismModelSetting } from '@framework/icubismmodelsetting';
import { CubismIdHandle } from '@framework/id/cubismid';
import { CubismFramework } from '@framework/live2dcubismframework';
import { CubismMatrix44 } from '@framework/math/cubismmatrix44';
import { CubismUserModel } from '@framework/model/cubismusermodel';
import {
  ACubismMotion,
  BeganMotionCallback,
  FinishedMotionCallback
} from '@framework/motion/acubismmotion';
import { CubismMotion } from '@framework/motion/cubismmotion';
import {
  CubismMotionQueueEntryHandle,
  InvalidMotionQueueEntryHandleValue
} from '@framework/motion/cubismmotionqueuemanager';
import { CubismUpdateScheduler } from '@framework/motion/cubismupdatescheduler';
import { CubismBreathUpdater } from '@framework/motion/cubismbreathupdater';
import { CubismLookUpdater } from '@framework/motion/cubismlookupdater';
import { CubismEyeBlinkUpdater } from '@framework/motion/cubismeyeblinkupdater';
import { CubismExpressionUpdater } from '@framework/motion/cubismexpressionupdater';
import { CubismPhysicsUpdater } from '@framework/motion/cubismphysicsupdater';
import { CubismPoseUpdater } from '@framework/motion/cubismposeupdater';
import { CubismLipSyncUpdater } from '@framework/motion/cubismlipsyncupdater';
import { csmRect } from '@framework/type/csmrectf';
import {
  CSM_ASSERT,
  CubismLogError,
  CubismLogInfo
} from '@framework/utils/cubismdebug';

import * as LAppDefine from './lappdefine';
import { LAppPal } from './lapppal';
import { TextureInfo } from './lapptexturemanager';
import { LAppWavFileHandler } from './lappwavfilehandler';
import { CubismMoc } from '@framework/model/cubismmoc';
import { LAppDelegate } from './lappdelegate';
import { LAppSubdelegate } from './lappsubdelegate';

// ── Model profile types ──
type MindIsleProfileEntry = {
  id: string; gain?: number; x?: number; y?: number;
  inertia?: number; reason?: string;
};
type MindIsleModelProfile = {
  modelId?: string;
  roles?: Record<string, MindIsleProfileEntry[]>;
  presets?: Record<string, Record<string, number>>;
};

enum LoadStep {
  LoadAssets,
  LoadModel,
  WaitLoadModel,
  LoadExpression,
  WaitLoadExpression,
  LoadPhysics,
  WaitLoadPhysics,
  LoadPose,
  WaitLoadPose,
  SetupEyeBlink,
  SetupBreath,
  LoadUserData,
  WaitLoadUserData,
  SetupEyeBlinkIds,
  SetupLipSyncIds,
  SetupLook,
  SetupLayout,
  LoadMotion,
  WaitLoadMotion,
  CompleteInitialize,
  CompleteSetupModel,
  LoadTexture,
  WaitLoadTexture,
  CompleteSetup
}

const MindIsleWatermarkOffParameterId = 'fase80';
const MindIsleWatermarkExpressionNames = new Set<string>([
  '\u6c34\u5370',
  '\u5de6\u6c34\u6bcd'
]);
const MindIsleWatermarkExpressionFiles = new Set<string>([
  '\u6c34\u5370.exp3.json',
  '\u5de6\u6c34\u6bcd.exp3.json'
]);
const MindIsleManualBlockedExpressionFiles = new Set<string>([
  '\u6c34\u5370.exp3.json',
  '\u5de6\u6c34\u6bcd.exp3.json',
  '\u53f3\u6c34\u6bcd.exp3.json'
]);

/**
 * ユーザーが実際に使用するモデルの実装クラス<br>
 * モデル生成、機能コンポーネント生成、更新処理とレンダリングの呼び出しを行う。
 */
export class LAppModel extends CubismUserModel {
  /**
   * model3.jsonが置かれたディレクトリとファイルパスからモデルを生成する
   * @param dir
   * @param fileName
   */
  public loadAssets(dir: string, fileName: string): void {
    this._modelHomeDir = dir;

    // Start loading the model profile in background.
    this._mindIsleProfileLoadPromise = this._loadModelProfile();

    fetch(`${this._modelHomeDir}${fileName}`)
      .then(response => response.arrayBuffer())
      .then(arrayBuffer => {
        const setting: ICubismModelSetting = new CubismModelSettingJson(
          arrayBuffer,
          arrayBuffer.byteLength
        );

        // ステートを更新
        this._state = LoadStep.LoadModel;

        // 結果を保存
        this.setupModel(setting);
      })
      .catch(error => {
        // model3.json読み込みでエラーが発生した時点で描画は不可能なので、setupせずエラーをcatchして何もしない
        CubismLogError(`Failed to load file ${this._modelHomeDir}${fileName}`);
      });
  }

  /**
   * model3.jsonからモデルを生成する。
   * model3.jsonの記述に従ってモデル生成、モーション、物理演算などのコンポーネント生成を行う。
   *
   * @param setting ICubismModelSettingのインスタンス
   */
  private setupModel(setting: ICubismModelSetting): void {
    this._updating = true;
    this._initialized = false;

    this._modelSetting = setting;

    // CubismModel
    if (this._modelSetting.getModelFileName() != '') {
      const modelFileName = this._modelSetting.getModelFileName();

      fetch(`${this._modelHomeDir}${modelFileName}`)
        .then(response => {
          if (response.ok) {
            return response.arrayBuffer();
          } else if (response.status >= 400) {
            CubismLogError(
              `Failed to load file ${this._modelHomeDir}${modelFileName}`
            );
            return new ArrayBuffer(0);
          }
        })
        .then(arrayBuffer => {
          this.loadModel(arrayBuffer, this._mocConsistency);
          this._state = LoadStep.LoadExpression;

          // callback
          loadCubismExpression();
        });

      this._state = LoadStep.WaitLoadModel;
    } else {
      LAppPal.printMessage('Model data does not exist.');
    }

    // Expression
    const loadCubismExpression = (): void => {
      if (this._modelSetting.getExpressionCount() > 0) {
        const count: number = this._modelSetting.getExpressionCount();

        for (let i = 0; i < count; i++) {
          const expressionName = this._modelSetting.getExpressionName(i);
          const expressionFileName =
            this._modelSetting.getExpressionFileName(i);

          if (
            this.isMindIsleWatermarkExpression(
              expressionName,
              expressionFileName
            )
          ) {
            console.log(
              `[Live2DViewer5] skip watermark expression from random pool: ${expressionFileName}`
            );
            this._expressionCount++;

            if (this._expressionCount >= count) {
              // Expression Updaterの追加
              if (this._expressionManager != null) {
                const expressionUpdater = new CubismExpressionUpdater(
                  this._expressionManager
                );
                this._updateScheduler.addUpdatableList(expressionUpdater);
              }

              this._state = LoadStep.LoadPhysics;

              // callback
              loadCubismPhysics();
            }
            continue;
          }

          fetch(`${this._modelHomeDir}${expressionFileName}`)
            .then(response => {
              if (response.ok) {
                return response.arrayBuffer();
              } else if (response.status >= 400) {
                CubismLogError(
                  `Failed to load file ${this._modelHomeDir}${expressionFileName}`
                );
                // ファイルが存在しなくてもresponseはnullを返却しないため、空のArrayBufferで対応する
                return new ArrayBuffer(0);
              }
            })
            .then(arrayBuffer => {
              const motion: ACubismMotion = this.loadExpression(
                arrayBuffer,
                arrayBuffer.byteLength,
                expressionName
              );

              if (this._expressions.get(expressionName) != null) {
                ACubismMotion.delete(this._expressions.get(expressionName));
                this._expressions.set(expressionName, null);
              }

              this._expressions.set(expressionName, motion);

              this._expressionCount++;

              if (this._expressionCount >= count) {
                // Expression Updaterの追加
                if (this._expressionManager != null) {
                  const expressionUpdater = new CubismExpressionUpdater(
                    this._expressionManager
                  );
                  this._updateScheduler.addUpdatableList(expressionUpdater);
                }

                this._state = LoadStep.LoadPhysics;

                // callback
                loadCubismPhysics();
              }
            });
        }
        this._state = LoadStep.WaitLoadExpression;
      } else {
        if (this._expressionManager != null) {
          const expressionUpdater = new CubismExpressionUpdater(
            this._expressionManager
          );
          this._updateScheduler.addUpdatableList(expressionUpdater);
        }

        this._state = LoadStep.LoadPhysics;

        // callback
        loadCubismPhysics();
      }
    };

    // Physics
    const loadCubismPhysics = (): void => {
      if (this._modelSetting.getPhysicsFileName() != '') {
        const physicsFileName = this._modelSetting.getPhysicsFileName();

        fetch(`${this._modelHomeDir}${physicsFileName}`)
          .then(response => {
            if (response.ok) {
              return response.arrayBuffer();
            } else if (response.status >= 400) {
              CubismLogError(
                `Failed to load file ${this._modelHomeDir}${physicsFileName}`
              );
              return new ArrayBuffer(0);
            }
          })
          .then(arrayBuffer => {
            this.loadPhysics(arrayBuffer, arrayBuffer.byteLength);

            // Physics Updaterの追加
            if (this._physics) {
              const physicsUpdater = new CubismPhysicsUpdater(this._physics);
              this._updateScheduler.addUpdatableList(physicsUpdater);
              console.log('[Live2DViewer5] physics loaded success');
              console.log('[Live2DViewer5] physics update enabled');
            }

            this._state = LoadStep.LoadPose;

            // callback
            loadCubismPose();
          });
        this._state = LoadStep.WaitLoadPhysics;
      } else {
        this._state = LoadStep.LoadPose;

        // callback
        loadCubismPose();
      }
    };

    // Pose
    const loadCubismPose = (): void => {
      if (this._modelSetting.getPoseFileName() != '') {
        const poseFileName = this._modelSetting.getPoseFileName();

        fetch(`${this._modelHomeDir}${poseFileName}`)
          .then(response => {
            if (response.ok) {
              return response.arrayBuffer();
            } else if (response.status >= 400) {
              CubismLogError(
                `Failed to load file ${this._modelHomeDir}${poseFileName}`
              );
              return new ArrayBuffer(0);
            }
          })
          .then(arrayBuffer => {
            this.loadPose(arrayBuffer, arrayBuffer.byteLength);

            // Pose Updaterの追加
            if (this._pose) {
              const poseUpdater = new CubismPoseUpdater(this._pose);
              this._updateScheduler.addUpdatableList(poseUpdater);
            }

            this._state = LoadStep.SetupEyeBlink;

            // callback
            setupEyeBlink();
          });
        this._state = LoadStep.WaitLoadPose;
      } else {
        this._state = LoadStep.SetupEyeBlink;

        // callback
        setupEyeBlink();
      }
    };

    // EyeBlink
    const setupEyeBlink = (): void => {
      if (this._modelSetting.getEyeBlinkParameterCount() > 0) {
        this._eyeBlink = CubismEyeBlink.create(this._modelSetting);
        const eyeBlinkUpdater = new CubismEyeBlinkUpdater(
          () => this._motionUpdated,
          this._eyeBlink
        );
        this._updateScheduler.addUpdatableList(eyeBlinkUpdater);
      }

      this._state = LoadStep.SetupBreath;

      // callback
      setupBreath();
    };

    // Breath
    const setupBreath = (): void => {
      console.log('[Live2DViewer5] skip generated breath; use model motion/physics resources');
      this._state = LoadStep.LoadUserData;

      // callback
      loadUserData();
    };

    // UserData
    const loadUserData = (): void => {
      if (this._modelSetting.getUserDataFile() != '') {
        const userDataFile = this._modelSetting.getUserDataFile();

        fetch(`${this._modelHomeDir}${userDataFile}`)
          .then(response => {
            if (response.ok) {
              return response.arrayBuffer();
            } else if (response.status >= 400) {
              CubismLogError(
                `Failed to load file ${this._modelHomeDir}${userDataFile}`
              );
              return new ArrayBuffer(0);
            }
          })
          .then(arrayBuffer => {
            this.loadUserData(arrayBuffer, arrayBuffer.byteLength);

            this._state = LoadStep.SetupEyeBlinkIds;

            // callback
            setupEyeBlinkIds();
          });

        this._state = LoadStep.WaitLoadUserData;
      } else {
        this._state = LoadStep.SetupEyeBlinkIds;

        // callback
        setupEyeBlinkIds();
      }
    };

    // EyeBlinkIds
    const setupEyeBlinkIds = (): void => {
      const eyeBlinkIdCount: number =
        this._modelSetting.getEyeBlinkParameterCount();

      this._eyeBlinkIds.length = eyeBlinkIdCount;
      for (let i = 0; i < eyeBlinkIdCount; ++i) {
        this._eyeBlinkIds[i] = this._modelSetting.getEyeBlinkParameterId(i);
      }

      this._state = LoadStep.SetupLipSyncIds;

      // callback
      setupLipSyncIds();
    };

    // LipSyncIds
    const setupLipSyncIds = (): void => {
      const lipSyncIdCount = this._modelSetting.getLipSyncParameterCount();

      this._lipSyncIds.length = lipSyncIdCount;
      for (let i = 0; i < lipSyncIdCount; ++i) {
        this._lipSyncIds[i] = this._modelSetting.getLipSyncParameterId(i);
      }

      // LipSync Updaterの追加
      if (this._lipSyncIds.length > 0) {
        const lipSyncUpdater = new CubismLipSyncUpdater(
          this._lipSyncIds,
          this._wavFileHandler
        );
        this._updateScheduler.addUpdatableList(lipSyncUpdater);
      }

      this._state = LoadStep.SetupLook;

      // callback
      setupLook();
    };

    // Look — dynamic profile-driven mapping.
    // If <modelId>_profile.json exists in modelHomeDir, use profile.roles.
    // Otherwise fall back to VTS-measured hardcoded parameters.
    const setupLook = (): void => {
      const look = CubismLook.create();
      const ids = CubismFramework.getIdManager();

      // Try loading profile synchronously via cached fetch result.
      // The profile fetch was kicked off in an earlier setup step.
      let paramsSource = 'fallback-vts-hardcoded';
      let lookParams: LookParameterData[] = [];

      // Wait for profile load to complete (or fail), then decide params.
      const onProfileReady = (): void => {
        if (this._mindIsleProfileLoaded && this._mindIsleProfile && this._mindIsleProfile.roles) {
          lookParams = buildLookParamsFromProfile(ids, this._mindIsleProfile);
          paramsSource = 'profile';
          console.log('[Live2DViewer5] look params source = profile');
          console.log('[Live2DViewer5] look params count = ' + lookParams.length);
        }
        if (lookParams.length === 0) {
          lookParams = buildFallbackLookParams(ids);
          console.log('[Live2DViewer5] look params source = fallback-vts-hardcoded');
        }
        look.setParameters(lookParams);
        const lookUpdater = new CubismLookUpdater(look, this._dragManager);
        this._updateScheduler.addUpdatableList(lookUpdater);
        console.log('[Live2DViewer5] look follow enabled (' + paramsSource + ')');
        finalizeUpdaters();
      };

      if (this._mindIsleProfileLoadPromise != null) {
        this._mindIsleProfileLoadPromise.then(onProfileReady).catch(onProfileReady);
      } else {
        onProfileReady();
      }
    };

    // Profile → LookParameterData[]
    function buildLookParamsFromProfile(
      ids: ReturnType<typeof CubismFramework.getIdManager>,
      profile: MindIsleModelProfile
    ): LookParameterData[] {
      const result: LookParameterData[] = [];
      if (!profile.roles) return result;

      const roleToAxis: Record<string, { axis: 'x' | 'y' }> = {
        eyeX: { axis: 'x' }, eyeY: { axis: 'y' },
        headX: { axis: 'x' }, headY: { axis: 'y' },
        bodyX: { axis: 'x' }, bodyY: { axis: 'y' },
        hairX: { axis: 'x' }, hairY: { axis: 'y' },
        clothX: { axis: 'x' }, clothY: { axis: 'y' },
        accessoryX: { axis: 'x' }, accessoryY: { axis: 'y' },
        extraX: { axis: 'x' }, extraY: { axis: 'y' },
      };

      const roles = profile.roles;
      const roleKeys = Object.keys(roles);
      for (let ri = 0; ri < roleKeys.length; ri++) {
        const role = roleKeys[ri];
        const entries = roles[role];
        if (!Array.isArray(entries)) continue;
        const axisInfo = roleToAxis[role];
        if (!axisInfo && role !== 'disabled') continue;

        for (let ei = 0; ei < entries.length; ei++) {
          const entry = entries[ei];
          if (!entry.id) continue;
          if (role === 'disabled') {
            result.push(new LookParameterData(ids.getId(entry.id), 0.0, 0.0));
          } else {
            const gain = Number(entry.gain ?? 1.0);
            const x = axisInfo!.axis === 'x' ? gain : 0.0;
            const y = axisInfo!.axis === 'y' ? gain : 0.0;
            result.push(new LookParameterData(ids.getId(entry.id), x, y));
          }
        }
      }
      return result;
    }

    // Fallback: VTS-measured hardcoded parameters (v2.6)
    function buildFallbackLookParams(
      ids: ReturnType<typeof CubismFramework.getIdManager>
    ): LookParameterData[] {
      return [
        new LookParameterData(ids.getId(CubismDefaultParameterId.ParamEyeBallX),     1.0, 0.0),
        new LookParameterData(ids.getId(CubismDefaultParameterId.ParamEyeBallY),     0.0, 0.35),
        new LookParameterData(ids.getId(CubismDefaultParameterId.ParamAngleX),       18.0, 0.0),
        new LookParameterData(ids.getId(CubismDefaultParameterId.ParamAngleY),       0.0, 1.0),
        new LookParameterData(ids.getId(CubismDefaultParameterId.ParamAngleZ),       18.0, 0.0),
        new LookParameterData(ids.getId('ParamAngleZ2'),        18.0, 0.0),
        new LookParameterData(ids.getId('ParamAngleX2'),        22.0, 0.0),
        new LookParameterData(ids.getId('ParamAngleX3'),        12.0, 0.0),
        new LookParameterData(ids.getId('ParamAngleX4'),        14.0, 0.0),
        new LookParameterData(ids.getId(CubismDefaultParameterId.ParamBodyAngleX),   8.0, 0.0),
        new LookParameterData(ids.getId(CubismDefaultParameterId.ParamBodyAngleZ),   8.0, 0.0),
        new LookParameterData(ids.getId('ParamBodyAngleX2'),     6.0, 0.0),
        new LookParameterData(ids.getId('ParamBodyAngleX7'),     7.0, 0.0),
        new LookParameterData(ids.getId('ParamBodyAngleZ3'),     5.0, 0.0),
        new LookParameterData(ids.getId('ParamBodyAngleZ5'),     5.0, 0.0),
        new LookParameterData(ids.getId('ParamBodyAngleZ4'),     4.0, 0.0),
        new LookParameterData(ids.getId('ParamHairX'),           0.4, 0.0),
        new LookParameterData(ids.getId('ParamHairX3'),          0.2, 0.0),
        new LookParameterData(ids.getId('ParamHairY2'),          0.0, 0.15),
        new LookParameterData(ids.getId('Paramdressx1'),         0.35, 0.0),
        new LookParameterData(ids.getId('Paramdressx2'),         0.30, 0.0),
        new LookParameterData(ids.getId('Paramdressx3'),         0.40, 0.0),
        new LookParameterData(ids.getId('ParamdressY1'),         0.0, 0.08),
        new LookParameterData(ids.getId('ParamdressY2'),         0.0, 0.08),
        new LookParameterData(ids.getId('ParamdressY3'),         0.0, 0.08),
        new LookParameterData(ids.getId(CubismDefaultParameterId.ParamBodyAngleY),   0.0, 0.0),
        new LookParameterData(ids.getId('ParamBodyAngleY2'),     0.0, 0.0),
        new LookParameterData(ids.getId('ParamBodyAngleY3'),     0.0, 0.0),
      ];
    }

    // UpdateScheduler最終化処理
    const finalizeUpdaters = (): void => {
      // 全てのUpdaterが追加されたのでUpdateSchedulerを最終ソート
      this._updateScheduler.sortUpdatableList();

      this._state = LoadStep.SetupLayout;

      // callback
      setupLayout();
    };

    // Layout
    const setupLayout = (): void => {
      const layout: Map<string, number> = new Map<string, number>();

      if (this._modelSetting == null || this._modelMatrix == null) {
        CubismLogError('Failed to setupLayout().');
        return;
      }

      this._modelSetting.getLayoutMap(layout);
      this._modelMatrix.setupFromLayout(layout);
      this._state = LoadStep.LoadMotion;

      // callback
      loadCubismMotion();
    };

    // Motion
    const loadCubismMotion = (): void => {
      this._state = LoadStep.WaitLoadMotion;
      this._model.saveParameters();
      this._allMotionCount = 0;
      this._motionCount = 0;
      const group: string[] = [];

      const motionGroupCount: number = this._modelSetting.getMotionGroupCount();

      // モーションの総数を求める
      for (let i = 0; i < motionGroupCount; i++) {
        group[i] = this._modelSetting.getMotionGroupName(i);
        this._allMotionCount += this._modelSetting.getMotionCount(group[i]);
      }

      // モーションの読み込み
      for (let i = 0; i < motionGroupCount; i++) {
        this.preLoadMotionGroup(group[i]);
      }

      // モーションがない場合
      if (motionGroupCount == 0) {
        this._state = LoadStep.LoadTexture;

        // 全てのモーションを停止する
        this._motionManager.stopAllMotions();

        this._updating = false;
        this._initialized = true;

        this.createRenderer(
          this._subdelegate.getCanvas().width,
          this._subdelegate.getCanvas().height
        );
        this.setupTextures();
        this.getRenderer().startUp(this._subdelegate.getGlManager().getGl());
        this.getRenderer().loadShaders(LAppDefine.ShaderPath);
      }
    };
  }

  /**
   * テクスチャユニットにテクスチャをロードする
   */
  private setupTextures(): void {
    // iPhoneでのアルファ品質向上のためTypescriptではpremultipliedAlphaを採用
    const usePremultiply = true;

    if (this._state == LoadStep.LoadTexture) {
      // テクスチャ読み込み用
      const textureCount: number = this._modelSetting.getTextureCount();

      for (
        let modelTextureNumber = 0;
        modelTextureNumber < textureCount;
        modelTextureNumber++
      ) {
        // テクスチャ名が空文字だった場合はロード・バインド処理をスキップ
        if (this._modelSetting.getTextureFileName(modelTextureNumber) == '') {
          console.log('getTextureFileName null');
          continue;
        }

        // WebGLのテクスチャユニットにテクスチャをロードする
        let texturePath =
          this._modelSetting.getTextureFileName(modelTextureNumber);
        texturePath = this._modelHomeDir + texturePath;

        // ロード完了時に呼び出すコールバック関数
        const onLoad = (textureInfo: TextureInfo): void => {
          this.getRenderer().bindTexture(modelTextureNumber, textureInfo.id);

          this._textureCount++;

          if (this._textureCount >= textureCount) {
            // ロード完了
            this._state = LoadStep.CompleteSetup;
            console.log('[Live2DViewer5] model loaded success');
            this.applyXingyueWatermarkOff();
            this.notifyMindIsleModelLoaded();
          }
        };

        // 読み込み
        this._subdelegate
          .getTextureManager()
          .createTextureFromPngFile(texturePath, usePremultiply, onLoad);
        this.getRenderer().setIsPremultipliedAlpha(usePremultiply);
      }

      this._state = LoadStep.WaitLoadTexture;
    }
  }

  /**
   * レンダラを再構築する
   */
  public reloadRenderer(): void {
    this.deleteRenderer();
    this.createRenderer(
      this._subdelegate.getCanvas().width,
      this._subdelegate.getCanvas().height
    );
    this.setupTextures();
  }

  /**
   * 更新
   */
  public update(): void {
    if (this._state != LoadStep.CompleteSetup) return;

    const deltaTimeSeconds: number = LAppPal.getDeltaTime();
    this._userTimeSeconds += deltaTimeSeconds;

    //--------------------------------------------------------------------------
    this._model.loadParameters(); // 前回セーブされた状態をロード

    // Reset motion updated flag each frame
    this._motionUpdated = false;

    if (this._motionManager.isFinished()) {
      // モーションの再生がない場合、待機モーションの中からランダムで再生する
      if (this._modelSetting.getMotionCount(LAppDefine.MotionGroupIdle) > 0) {
        this.startRandomMotion(
          LAppDefine.MotionGroupIdle,
          LAppDefine.PriorityIdle
        );
      } else {
        this.startMindIsleIdleMotion();
      }
    } else {
      this._motionUpdated = this._motionManager.updateMotion(
        this._model,
        deltaTimeSeconds
      ); // モーションを更新
    }
    this._model.saveParameters(); // 状態を保存
    //--------------------------------------------------------------------------

    // ── Update order: physics → look → pose → watermark → model.update() ──
    this._updateScheduler.onLateUpdate(this._model, deltaTimeSeconds);

    // Direct setParameterValueById RIGHT before model.update() — guarantees
    // parameters are never overwritten before render.
    const dx = this._dragManager.getX();
    const dy = this._dragManager.getY();
    if (this._mindIsleProfile && (Math.abs(dx) > 0.0001 || Math.abs(dy) > 0.0001)) {
      this._applyMindIsleLookDirect(dx, dy);
    }

    this.applyXingyueWatermarkOff();
    this._model.update();
  }

  /** Direct setParameterValueById for profile params, right before model.update(). */
  private _applyMindIsleLookDirect(dragX: number, dragY: number): void {
    if (!this._mindIsleProfile || !this._mindIsleProfile.roles) return;
    const ids = CubismFramework.getIdManager();
    const roleToAxis: Record<string, 'x'|'y'> = {
      eyeX:'x', eyeY:'y', headX:'x', headY:'y', bodyX:'x', bodyY:'y',
      hairX:'x', hairY:'y', clothX:'x', clothY:'y',
      accessoryX:'x', accessoryY:'y', extraX:'x', extraY:'y',
    };
    for (const role of Object.keys(this._mindIsleProfile.roles)) {
      if (role === 'disabled') continue;
      const axis = roleToAxis[role];
      if (!axis) continue;
      const entries = this._mindIsleProfile.roles[role];
      for (const entry of entries) {
        if (!entry.id || entry.gain === undefined || entry.gain <= 0) continue;
        try {
          const paramId = ids.getId(entry.id);
          const gain = entry.gain;
          const value = axis === 'x' ? dragX * gain : dragY * gain;
          this._model.setParameterValueById(paramId, value);
        } catch (_) { /* param not found, skip */ }
      }
    }
  }

  private _logLookParamValues(when: string): void {
    // Only enable for xiaohushi debugging — no impact on other models.
    const modelId: string = (window as any).MindIsleViewer5ModelId || '';
    if (modelId !== 'xiaohushi') return;

    if (this._mindIsleDebugParamIds.length === 0) {
      const ids = CubismFramework.getIdManager();
      const keys = ['ParamAngleX', 'ParamAngleZ', 'ParamBodyAngleX',
                    'ParamEyeBallX', 'ParamAngleY', 'ParamBodyAngleZ'];
      for (const k of keys) {
        try { this._mindIsleDebugParamIds.push(ids.getId(k)); } catch (_) { }
      }
    }
    if (this._mindIsleDebugFrame === 0 && when === 'before') {
      console.log('[Live2DViewer5] debug params: ' +
        this._mindIsleDebugParamIds.map(id => id.getString()).join(', '));
    }
    for (const id of this._mindIsleDebugParamIds) {
      const val = this._model.getParameterValueById(id);
      if (val === null || val === undefined || Number.isNaN(Number(val))) {
        console.log(`[Live2DViewer5] ${when} f=${this._mindIsleDebugFrame} ${id.getString()}=<null/invalid>`);
      } else {
        console.log(`[Live2DViewer5] ${when} f=${this._mindIsleDebugFrame} ${id.getString()}=${Number(val).toFixed(3)}`);
      }
    }
  }

  private applyXingyueWatermarkOff(): void {
    if (this._model == null) {
      return;
    }

    try {
      if (this._mindIsleWatermarkOffId == null) {
        this._mindIsleWatermarkOffId =
          CubismFramework.getIdManager().getId(
            MindIsleWatermarkOffParameterId
          );
        console.log('[Live2DViewer5] apply watermark expression: 水印.exp3.json');
      }

      this._model.setParameterValueById(this._mindIsleWatermarkOffId, 1.0);

      if (!this._mindIsleWatermarkLogged) {
        console.log('[Live2DViewer5] set parameter fase80 = 1.0');
        this._mindIsleWatermarkLogged = true;
      }
    } catch (error) {
      console.error('[Live2DViewer5] apply watermark expression failed', error);
    }
  }

  public async applyExpressionByFile(expressionFile: string): Promise<void> {
    const normalizedFile = this.normalizeMindIsleExpressionName(expressionFile);
    if (MindIsleManualBlockedExpressionFiles.has(normalizedFile)) {
      console.log(`[Live2DViewer5] skip special expression: ${normalizedFile}`);
      this.applyXingyueWatermarkOff();
      return;
    }

    const expressionUrl = `${this._modelHomeDir}${normalizedFile}`;
    console.log(`[Live2DViewer5] apply expression: ${normalizedFile}`);

    try {
      let motion: ACubismMotion = this._mindIsleDynamicExpressions.get(normalizedFile);

      if (motion == null) {
        const response = await fetch(expressionUrl);
        if (!response.ok) {
          // Expression not found — model simply doesn't have this file.
          // Log once and skip silently; do NOT trigger load failed.
          if (!this._mindIsleExpressionNotFound.has(normalizedFile)) {
            this._mindIsleExpressionNotFound.add(normalizedFile);
            console.log(`[Live2DViewer5] expression not found, skipping: ${normalizedFile}`);
          }
          return;
        }

        const arrayBuffer = await response.arrayBuffer();
        motion = this.loadExpression(
          arrayBuffer,
          arrayBuffer.byteLength,
          normalizedFile
        );

        if (motion == null) {
          throw new Error(`loadExpression returned null for ${normalizedFile}`);
        }

        this._mindIsleDynamicExpressions.set(normalizedFile, motion);
      }

      this._expressionManager.startMotion(motion, false);
      this.applyXingyueWatermarkOff();
      console.log('[Live2DViewer5] expression start success');
      console.log('[Live2DViewer5] apply watermark off fase80=1.0');
    } catch (error) {
      console.error('[Live2DViewer5] apply expression failed');
      console.error('[Live2DViewer5] error name =', (error as Error)?.name);
      console.error('[Live2DViewer5] error message =', (error as Error)?.message);
      console.error('[Live2DViewer5] error stack =', (error as Error)?.stack);
    }
  }

  /** Load the model profile (_profile.json) from the model directory. */
  private async _loadModelProfile(): Promise<void> {
    if (this._mindIsleProfileLoaded) return;
    const modelId: string = (window as any).MindIsleViewer5ModelId || '';
    const candidates: string[] = [];
    if (modelId) {
      candidates.push(`${this._modelHomeDir}${modelId}_profile.json`);
    }
    const base = modelId.replace(/_safe$/, '');
    if (base !== modelId) {
      candidates.push(`${this._modelHomeDir}${base}_profile.json`);
    }
    // Generic fallback: profile.json (recommended convention for new models)
    candidates.push(`${this._modelHomeDir}profile.json`);
    console.log('[Live2DViewer5] profile loading: ' + (candidates[0] || '(no candidates)'));
    for (const url of candidates) {
      try {
        const r = await fetch(url);
        if (r.ok) {
          const json = await r.json();
          this._mindIsleProfile = json as MindIsleModelProfile;
          console.log('[Live2DViewer5] profile loaded success: ' + (json.modelId || 'unknown'));
          break;
        }
      } catch (_) { /* try next */ }
    }
    if (!this._mindIsleProfile) {
      console.log('[Live2DViewer5] profile not found, use fallback');
    }
    this._mindIsleProfileLoaded = true;
  }

  public setMindIsleLookTarget(x: number, y: number): void {
    this.setDragging(x, y);
  }

  public resetMindIsleLookTarget(): void {
    this.setDragging(0, 0);
  }

  public setMindIsleTalkState(talking: boolean): void {
    console.log(
      '[Live2DViewer5] talk state ignored; no model-authored talk motion is configured',
      talking
    );
  }

  public playMindIsleTapReaction(_x: number, _y: number): void {
    // Use model-authored expressions only \u2014 no hardcoded file names.
    const expCount = this._modelSetting.getExpressionCount();
    if (expCount > 0) {
      const idx = Math.floor(Math.random() * expCount);
      const expName = this._modelSetting.getExpressionName(idx);
      if (expName) this.applyExpressionByFile(expName);
    }
  }

  private notifyMindIsleModelLoaded(): void {
    try {
      const modelId = (
        window as unknown as {
          MindIsleViewer5ModelId?: string;
        }
      ).MindIsleViewer5ModelId ?? 'xingyue_shuimu';

      const bridge = window as unknown as {
        AndroidLive2D?: { onModelLoaded?: (modelId: string) => void };
        MindIsleLive2DBridge?: { onModelLoaded?: (modelId: string) => void };
      };

      bridge.AndroidLive2D?.onModelLoaded?.(modelId);
      bridge.MindIsleLive2DBridge?.onModelLoaded?.(modelId);
    } catch (error) {
      console.error('[Live2DViewer5] notify model loaded failed', error);
    }
  }

  public setMindIsleChibiEnabled(enabled: boolean): void {
    if (enabled && !this._mindIsleChibiEnabled) {
      this.applyExpressionByFile('\u53d8\u5c0f.exp3.json');
    }
    this._mindIsleChibiEnabled = enabled;
  }

  /**
   * 引数で指定したモーションの再生を開始する
   * @param group モーショングループ名
   * @param no グループ内の番号
   * @param priority 優先度
   * @param onFinishedMotionHandler モーション再生終了時に呼び出されるコールバック関数
   * @return 開始したモーションの識別番号を返す。個別のモーションが終了したか否かを判定するisFinished()の引数で使用する。開始できない時は[-1]
   */
  public startMotion(
    group: string,
    no: number,
    priority: number,
    onFinishedMotionHandler?: FinishedMotionCallback,
    onBeganMotionHandler?: BeganMotionCallback
  ): CubismMotionQueueEntryHandle {
    if (priority == LAppDefine.PriorityForce) {
      this._motionManager.setReservePriority(priority);
    } else if (!this._motionManager.reserveMotion(priority)) {
      if (this._debugMode) {
        LAppPal.printMessage("[APP]can't start motion.");
      }
      return InvalidMotionQueueEntryHandleValue;
    }

    const motionFileName = this._modelSetting.getMotionFileName(group, no);

    // ex) idle_0
    const name = `${group}_${no}`;
    let motion: CubismMotion = this._motions.get(name) as CubismMotion;
    let autoDelete = false;

    if (motion == null) {
      fetch(`${this._modelHomeDir}${motionFileName}`)
        .then(response => {
          if (response.ok) {
            return response.arrayBuffer();
          } else if (response.status >= 400) {
            CubismLogError(
              `Failed to load file ${this._modelHomeDir}${motionFileName}`
            );
            return new ArrayBuffer(0);
          }
        })
        .then(arrayBuffer => {
          motion = this.loadMotion(
            arrayBuffer,
            arrayBuffer.byteLength,
            null,
            onFinishedMotionHandler,
            onBeganMotionHandler,
            this._modelSetting,
            group,
            no,
            this._motionConsistency
          );
        });

      if (motion) {
        motion.setEffectIds(this._eyeBlinkIds, this._lipSyncIds);
        autoDelete = true; // 終了時にメモリから削除
      } else {
        CubismLogError("Can't start motion {0} .", motionFileName);
        // ロードできなかったモーションのReservePriorityをリセットする
        this._motionManager.setReservePriority(LAppDefine.PriorityNone);
        return InvalidMotionQueueEntryHandleValue;
      }
    } else {
      motion.setBeganMotionHandler(onBeganMotionHandler);
      motion.setFinishedMotionHandler(onFinishedMotionHandler);
    }

    //voice
    const voice = this._modelSetting.getMotionSoundFileName(group, no);
    if (voice.localeCompare('') != 0) {
      let path = voice;
      path = this._modelHomeDir + path;
      this._wavFileHandler.start(path);
    }

    if (this._debugMode) {
      LAppPal.printMessage(`[APP]start motion: [${group}_${no}]`);
    }
    return this._motionManager.startMotionPriority(
      motion,
      autoDelete,
      priority
    );
  }

  /**
   * ランダムに選ばれたモーションの再生を開始する。
   * @param group モーショングループ名
   * @param priority 優先度
   * @param onFinishedMotionHandler モーション再生終了時に呼び出されるコールバック関数
   * @return 開始したモーションの識別番号を返す。個別のモーションが終了したか否かを判定するisFinished()の引数で使用する。開始できない時は[-1]
   */
  public async playMotionByFile(motionFile: string): Promise<void> {
    const normalizedFile = this.normalizeMindIsleExpressionName(motionFile);
    console.log(`[Live2DViewer5] play motion: ${normalizedFile}`);

    try {
      await this.startMindIsleMotionFile(
        normalizedFile,
        LAppDefine.PriorityForce
      );
      this.applyXingyueWatermarkOff();
      console.log('[Live2DViewer5] motion played success');
    } catch (error) {
      console.error('[Live2DViewer5] play motion failed');
      console.error('[Live2DViewer5] error name =', (error as Error)?.name);
      console.error('[Live2DViewer5] error message =', (error as Error)?.message);
      console.error('[Live2DViewer5] error stack =', (error as Error)?.stack);
    }
  }

  private startMindIsleIdleMotion(): void {
    if (this._mindIsleIdleMotionDisabled) return;
    if (this._mindIsleIdleMotionLoading || this._mindIsleIdleMotionNotFound) return;

    // Check if model has any motion group configured
    const hasModelMotions = this._modelSetting.getMotionCount('Idle') > 0;
    if (!hasModelMotions) {
      this._mindIsleIdleMotionDisabled = true;
      console.log('[Live2DViewer5] idle motion disabled: no valid motion files');
      return;
    }

    this.startMindIsleMotionFile('Scene1.motion3.json', LAppDefine.PriorityIdle)
      .then(() => {
        console.log('[Live2DViewer5] idle motion start: Scene1.motion3.json');
      })
      .catch(_error => {
        if (!this._mindIsleIdleMotionWarned) {
          console.warn('[Live2DViewer5] idle motion disabled: motion fetch failed');
          this._mindIsleIdleMotionWarned = true;
        }
        this._mindIsleIdleMotionDisabled = true;
      });
  }

  private async startMindIsleMotionFile(
    normalizedFile: string,
    priority: number
  ): Promise<void> {
    if (priority == LAppDefine.PriorityIdle) {
      this._mindIsleIdleMotionLoading = true;
    }

    try {
      let motion = this._mindIsleManualMotions.get(normalizedFile);
      if (motion == null) {
        const response = await fetch(`${this._modelHomeDir}${normalizedFile}`);
        if (!response.ok) {
          throw new Error(
            `fetch ${normalizedFile} failed: ${response.status} ${response.statusText}`
          );
        }

        const arrayBuffer = await response.arrayBuffer();
        motion = this.loadMotion(
          arrayBuffer,
          arrayBuffer.byteLength,
          `mindisle_${normalizedFile}`,
          null,
          null,
          null,
          null,
          null,
          this._motionConsistency
        );

        if (motion == null) {
          throw new Error(`loadMotion returned null for ${normalizedFile}`);
        }

        motion.setEffectIds(this._eyeBlinkIds, this._lipSyncIds);
        this._mindIsleManualMotions.set(normalizedFile, motion);
      }

      this._motionManager.setReservePriority(priority);
      this._motionManager.startMotionPriority(motion, false, priority);
    } finally {
      if (priority == LAppDefine.PriorityIdle) {
        this._mindIsleIdleMotionLoading = false;
        this._mindIsleIdleMotionNotFound = false;
        this._mindIsleIdleMotionDisabled = false;
        this._mindIsleIdleMotionWarned = false;
      }
    }
  }

  public startRandomMotion(
    group: string,
    priority: number,
    onFinishedMotionHandler?: FinishedMotionCallback,
    onBeganMotionHandler?: BeganMotionCallback
  ): CubismMotionQueueEntryHandle {
    if (this._modelSetting.getMotionCount(group) == 0) {
      return InvalidMotionQueueEntryHandleValue;
    }

    const no: number = Math.floor(
      Math.random() * this._modelSetting.getMotionCount(group)
    );

    return this.startMotion(
      group,
      no,
      priority,
      onFinishedMotionHandler,
      onBeganMotionHandler
    );
  }

  /**
   * 引数で指定した表情モーションをセットする
   *
   * @param expressionId 表情モーションのID
   */
  public setExpression(expressionId: string): void {
    if (this.isMindIsleWatermarkExpression(expressionId)) {
      console.log(
        `[Live2DViewer5] skip direct watermark expression: ${expressionId}`
      );
      this.applyXingyueWatermarkOff();
      return;
    }

    const motion: ACubismMotion = this._expressions.get(expressionId);

    if (this._debugMode) {
      LAppPal.printMessage(`[APP]expression: [${expressionId}]`);
    }

    if (motion != null) {
      this._expressionManager.startMotion(motion, false);
    } else {
      if (this._debugMode) {
        LAppPal.printMessage(`[APP]expression[${expressionId}] is null`);
      }
    }
  }

  /**
   * ランダムに選ばれた表情モーションをセットする
   */
  public setRandomExpression(): void {
    if (this._expressions.size == 0) {
      return;
    }

    const safeExpressions = [...this._expressions.keys()].filter(
      expressionId => !this.isMindIsleWatermarkExpression(expressionId)
    );

    if (safeExpressions.length == 0) {
      this.applyXingyueWatermarkOff();
      return;
    }

    const no: number = Math.floor(Math.random() * safeExpressions.length);
    this.setExpression(safeExpressions[no]);
  }

  private isMindIsleWatermarkExpression(
    expressionName: string,
    expressionFileName?: string
  ): boolean {
    const normalizedName = this.normalizeMindIsleExpressionName(expressionName);
    const normalizedFileName =
      expressionFileName == null
        ? ''
        : this.normalizeMindIsleExpressionName(expressionFileName);

    return (
      MindIsleWatermarkExpressionNames.has(normalizedName) ||
      MindIsleWatermarkExpressionFiles.has(normalizedName) ||
      MindIsleWatermarkExpressionFiles.has(normalizedFileName)
    );
  }

  private normalizeMindIsleExpressionName(value: string): string {
    return value.replace(/\\/g, '/').split('/').pop() ?? value;
  }

  /**
   * イベントの発火を受け取る
   */
  public motionEventFired(eventValue: string): void {
    CubismLogInfo('{0} is fired on LAppModel!!', eventValue);
  }

  /**
   * 当たり判定テスト
   * 指定ＩＤの頂点リストから矩形を計算し、座標をが矩形範囲内か判定する。
   *
   * @param hitArenaName  当たり判定をテストする対象のID
   * @param x             判定を行うX座標
   * @param y             判定を行うY座標
   */
  public hitTest(hitArenaName: string, x: number, y: number): boolean {
    // 透明時は当たり判定無し。
    if (this._opacity < 1) {
      return false;
    }

    const count: number = this._modelSetting.getHitAreasCount();

    for (let i = 0; i < count; i++) {
      if (this._modelSetting.getHitAreaName(i) == hitArenaName) {
        const drawId: CubismIdHandle = this._modelSetting.getHitAreaId(i);
        return this.isHit(drawId, x, y);
      }
    }

    return false;
  }

  /**
   * モーションデータをグループ名から一括でロードする。
   * モーションデータの名前は内部でModelSettingから取得する。
   *
   * @param group モーションデータのグループ名
   */
  public preLoadMotionGroup(group: string): void {
    for (let i = 0; i < this._modelSetting.getMotionCount(group); i++) {
      const motionFileName = this._modelSetting.getMotionFileName(group, i);

      // ex) idle_0
      const name = `${group}_${i}`;
      if (this._debugMode) {
        LAppPal.printMessage(
          `[APP]load motion: ${motionFileName} => [${name}]`
        );
      }

      fetch(`${this._modelHomeDir}${motionFileName}`)
        .then(response => {
          if (response.ok) {
            return response.arrayBuffer();
          } else if (response.status >= 400) {
            CubismLogError(
              `Failed to load file ${this._modelHomeDir}${motionFileName}`
            );
            return new ArrayBuffer(0);
          }
        })
        .then(arrayBuffer => {
          const tmpMotion: CubismMotion = this.loadMotion(
            arrayBuffer,
            arrayBuffer.byteLength,
            name,
            null,
            null,
            this._modelSetting,
            group,
            i,
            this._motionConsistency
          );

          if (tmpMotion != null) {
            tmpMotion.setEffectIds(this._eyeBlinkIds, this._lipSyncIds);

            if (this._motions.get(name) != null) {
              ACubismMotion.delete(this._motions.get(name));
            }

            this._motions.set(name, tmpMotion);

            this._motionCount++;
          } else {
            // loadMotionできなかった場合はモーションの総数がずれるので1つ減らす
            this._allMotionCount--;
          }

          if (this._motionCount >= this._allMotionCount) {
            this._state = LoadStep.LoadTexture;

            // 全てのモーションを停止する
            this._motionManager.stopAllMotions();

            this._updating = false;
            this._initialized = true;

            this.createRenderer(
              this._subdelegate.getCanvas().width,
              this._subdelegate.getCanvas().height
            );
            this.setupTextures();
            this.getRenderer().startUp(
              this._subdelegate.getGlManager().getGl()
            );
            this.getRenderer().loadShaders(LAppDefine.ShaderPath);
          }
        });
    }
  }

  /**
   * すべてのモーションデータを解放する。
   */
  public releaseMotions(): void {
    this._motions.clear();
  }

  /**
   * 全ての表情データを解放する。
   */
  public releaseExpressions(): void {
    this._expressions.clear();
  }

  /**
   * モデルを描画する処理。モデルを描画する空間のView-Projection行列を渡す。
   */
  public doDraw(): void {
    if (this._model == null) return;

    // キャンバスサイズを渡す
    const canvas = this._subdelegate.getCanvas();
    const viewport: number[] = [0, 0, canvas.width, canvas.height];

    this.getRenderer().setRenderState(
      this._subdelegate.getFrameBuffer(),
      viewport
    );
    this.getRenderer().drawModel(LAppDefine.ShaderPath);
  }

  /**
   * モデルを描画する処理。モデルを描画する空間のView-Projection行列を渡す。
   */
  public draw(matrix: CubismMatrix44): void {
    if (this._model == null) {
      return;
    }

    // 各読み込み終了後
    if (this._state == LoadStep.CompleteSetup) {
      matrix.multiplyByMatrix(this._modelMatrix);

      this.getRenderer().setMvpMatrix(matrix);

      this.doDraw();
    }
  }

  public async hasMocConsistencyFromFile() {
    CSM_ASSERT(this._modelSetting.getModelFileName().localeCompare(``));

    // CubismModel
    if (this._modelSetting.getModelFileName() != '') {
      const modelFileName = this._modelSetting.getModelFileName();

      const response = await fetch(`${this._modelHomeDir}${modelFileName}`);
      const arrayBuffer = await response.arrayBuffer();

      this._consistency = CubismMoc.hasMocConsistency(arrayBuffer);

      if (!this._consistency) {
        CubismLogInfo('Inconsistent MOC3.');
      } else {
        CubismLogInfo('Consistent MOC3.');
      }

      return this._consistency;
    } else {
      LAppPal.printMessage('Model data does not exist.');
    }
  }

  public setSubdelegate(subdelegate: LAppSubdelegate): void {
    this._subdelegate = subdelegate;
  }

  /**
   * デストラクタに相当する処理のオーバーライド
   */
  public release(): void {
    if (this._look) {
      CubismLook.delete(this._look);
      this._look = null;
    }
    if (this._updateScheduler) {
      this._updateScheduler.release();
    }
    super.release();
  }

  /**
   * コンストラクタ
   */
  public constructor() {
    super();

    this._modelSetting = null;
    this._modelHomeDir = null;
    this._userTimeSeconds = 0.0;

    this._eyeBlinkIds = new Array<CubismIdHandle>();
    this._lipSyncIds = new Array<CubismIdHandle>();

    this._motions = new Map<string, ACubismMotion>();
    this._expressions = new Map<string, ACubismMotion>();

    this._hitArea = new Array<csmRect>();
    this._userArea = new Array<csmRect>();

    this._idParamAngleX = CubismFramework.getIdManager().getId(
      CubismDefaultParameterId.ParamAngleX
    );
    this._idParamAngleY = CubismFramework.getIdManager().getId(
      CubismDefaultParameterId.ParamAngleY
    );
    this._idParamAngleZ = CubismFramework.getIdManager().getId(
      CubismDefaultParameterId.ParamAngleZ
    );
    this._idParamBodyAngleX = CubismFramework.getIdManager().getId(
      CubismDefaultParameterId.ParamBodyAngleX
    );

    if (LAppDefine.MOCConsistencyValidationEnable) {
      this._mocConsistency = true;
    }

    if (LAppDefine.MotionConsistencyValidationEnable) {
      this._motionConsistency = true;
    }

    this._state = LoadStep.LoadAssets;
    this._expressionCount = 0;
    this._textureCount = 0;
    this._motionCount = 0;
    this._allMotionCount = 0;
    this._wavFileHandler = new LAppWavFileHandler();
    this._consistency = false;
    this._look = null;
    this._updateScheduler = new CubismUpdateScheduler();
    this._motionUpdated = false;
    this._mindIsleWatermarkOffId = null;
    this._mindIsleWatermarkLogged = false;
    this._mindIsleChibiEnabled = false;
    this._mindIsleDynamicExpressions = new Map<string, ACubismMotion>();
    this._mindIsleManualMotions = new Map<string, CubismMotion>();
    this._mindIsleIdleMotionLoading = false;
  }

  private _updateScheduler: CubismUpdateScheduler; // アップデートスケジューラー
  private _motionUpdated: boolean; // モーション更新フラグ
  private _subdelegate: LAppSubdelegate; // サブデリゲート

  _modelSetting: ICubismModelSetting; // モデルセッティング情報
  _modelHomeDir: string; // モデルセッティングが置かれたディレクトリ
  _userTimeSeconds: number; // デルタ時間の積算値[秒]

  _eyeBlinkIds: Array<CubismIdHandle>; // モデルに設定された瞬き機能用パラメータID
  _lipSyncIds: Array<CubismIdHandle>; // モデルに設定されたリップシンク機能用パラメータID

  _motions: Map<string, ACubismMotion>; // 読み込まれているモーションのリスト
  _expressions: Map<string, ACubismMotion>; // 読み込まれている表情のリスト

  _hitArea: Array<csmRect>;
  _userArea: Array<csmRect>;
  private _mindIsleWatermarkOffId: CubismIdHandle | null;
  private _mindIsleWatermarkLogged: boolean;
  private _mindIsleChibiEnabled: boolean;
  private _mindIsleDynamicExpressions: Map<string, ACubismMotion>;
  private _mindIsleManualMotions: Map<string, CubismMotion>;
  private _mindIsleIdleMotionLoading: boolean;
  private _mindIsleIdleMotionNotFound = false;
  private _mindIsleIdleMotionDisabled = false;
  private _mindIsleIdleMotionWarned = false;
  private _mindIsleExpressionNotFound = new Set<string>();
  private _mindIsleDebugFrame = 0;
  private _mindIsleDebugParamIds: CubismIdHandle[] = [];
  private _mindIsleProfile: MindIsleModelProfile | null = null;
  private _mindIsleProfileLoaded = false;
  private _mindIsleProfileLoadPromise: Promise<void> | null = null;

  _idParamAngleX: CubismIdHandle; // パラメータID: ParamAngleX
  _idParamAngleY: CubismIdHandle; // パラメータID: ParamAngleY
  _idParamAngleZ: CubismIdHandle; // パラメータID: ParamAngleZ
  _idParamBodyAngleX: CubismIdHandle; // パラメータID: ParamBodyAngleX

  _look: CubismLook; // ドラッグ追従

  _state: LoadStep; // 現在のステータス管理用
  _expressionCount: number; // 表情データカウント
  _textureCount: number; // テクスチャカウント
  _motionCount: number; // モーションデータカウント
  _allMotionCount: number; // モーション総数
  _wavFileHandler: LAppWavFileHandler; //wavファイルハンドラ
  _consistency: boolean; // MOC3整合性チェック管理用
}
