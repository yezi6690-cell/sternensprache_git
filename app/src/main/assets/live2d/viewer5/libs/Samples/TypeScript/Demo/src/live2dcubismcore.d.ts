declare namespace Live2DCubismCore {
  type csmLogFunction = (message: string) => void;
  type csmParameterType = number;
  type Model = any;
  type Moc = any;
}

declare const Live2DCubismCore: {
  Logging: {
    csmSetLogFunction(logFunction: Live2DCubismCore.csmLogFunction): void;
    csmGetLogFunction(): Live2DCubismCore.csmLogFunction | null;
  };
  Version: {
    csmGetVersion(): number;
    csmGetMocVersion(buffer: ArrayBuffer): number;
    csmGetLatestMocVersion(): number;
  };
  Moc: {
    fromArrayBuffer(buffer: ArrayBuffer): Live2DCubismCore.Moc | null;
    prototype: any;
  };
  Memory: any;
  Utils: any;
  Model: any;
  ColorBlendType_Normal: number;
  ColorBlendType_AddGlow: number;
  ColorBlendType_Add: number;
  ColorBlendType_Darken: number;
  ColorBlendType_Multiply: number;
  ColorBlendType_ColorBurn: number;
  ColorBlendType_LinearBurn: number;
  ColorBlendType_Lighten: number;
  ColorBlendType_Screen: number;
  ColorBlendType_ColorDodge: number;
  ColorBlendType_Overlay: number;
  ColorBlendType_SoftLight: number;
  ColorBlendType_HardLight: number;
  ColorBlendType_LinearLight: number;
  ColorBlendType_Hue: number;
  ColorBlendType_Color: number;
  ColorBlendType_AddCompatible: number;
  ColorBlendType_MultiplyCompatible: number;
};
