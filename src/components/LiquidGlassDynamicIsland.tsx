import React, { useState, useRef, useEffect } from 'react';
import {
  Mic,
  Volume2,
  ShieldCheck,
  Sparkles,
  X,
  Minimize2,
  Check,
  Lock,
  Navigation,
  SkipBack,
  SkipForward,
  Pause,
  Play,
  RotateCcw,
  Square,
} from 'lucide-react';
import { OrbVisualState } from '../types/jarvis';

export interface DynamicIslandCardData {
  domain: string;
  languageLabel: string;
  actionPerformed: string;
  structuredTitle: string;
  structuredBullets: string[];
  requiresAppPermissionId?: string;
  requiresAppPermissionName?: string;
  permissionScopeDescription?: string;
}

export interface IslandCustomTheme {
  id: string;
  name: string;
  primaryHex: string;
  secondaryHex: string;
  pillScale: 'compact' | 'standard' | 'wide';
  glassBlurPx: number;
}

interface LiquidGlassDynamicIslandProps {
  orbState: OrbVisualState;
  isExpanded: boolean;
  setIsExpanded: (expanded: boolean) => void;
  userTranscript: string;
  jarvisResponse: string;
  waveformBars: number[];
  cardData: DynamicIslandCardData | null;
  idleSecondsRemaining: number | null;
  customTheme: IslandCustomTheme;
  // Task Navigation Props
  activeTaskTitle: string | null;
  currentStepNumber: number;
  totalSteps: number;
  isGuidancePaused: boolean;
  onPrevStep: () => void;
  onNextStep: () => void;
  onPauseResumeGuidance: () => void;
  onRepeatStep: () => void;
  onStopGuidance: () => void;
  // App Permission Props
  onGrantAppPermission: (appId: string) => void;
  onDenyAppPermission: () => void;
  onDismissIsland: () => void;
}

export const LiquidGlassDynamicIsland: React.FC<LiquidGlassDynamicIslandProps> = ({
  orbState,
  isExpanded,
  setIsExpanded,
  userTranscript,
  jarvisResponse,
  waveformBars,
  cardData,
  idleSecondsRemaining,
  customTheme,
  activeTaskTitle,
  currentStepNumber,
  totalSteps,
  isGuidancePaused,
  onPrevStep,
  onNextStep,
  onPauseResumeGuidance,
  onRepeatStep,
  onStopGuidance,
  onGrantAppPermission,
  onDenyAppPermission,
  onDismissIsland,
}) => {
  const [offset, setOffset] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const [dragging, setDragging] = useState(false);
  const dragOriginRef = useRef<{ x: number; y: number; startX: number; startY: number } | null>(
    null
  );

  useEffect(() => {
    if (!dragging) return;
    const onMove = (e: PointerEvent) => {
      if (!dragOriginRef.current) return;
      const dx = e.clientX - dragOriginRef.current.x;
      const dy = e.clientY - dragOriginRef.current.y;
      setOffset({
        x: Math.max(-36, Math.min(36, dragOriginRef.current.startX + dx)),
        y: Math.max(0, Math.min(280, dragOriginRef.current.startY + dy)),
      });
    };
    const onUp = () => {
      setDragging(false);
      dragOriginRef.current = null;
    };
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
    return () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
    };
  }, [dragging]);

  const handlePointerDown = (e: React.PointerEvent) => {
    if ((e.target as HTMLElement).closest('button')) return;
    setDragging(true);
    dragOriginRef.current = {
      x: e.clientX,
      y: e.clientY,
      startX: offset.x,
      startY: offset.y,
    };
  };

  const pillWidthClass =
    customTheme.pillScale === 'compact'
      ? 'px-3.5 py-1.5'
      : customTheme.pillScale === 'wide'
      ? 'px-6 py-2.5'
      : 'px-4 py-2';

  // Compact Dynamic Island Pill when collapsed
  if (!isExpanded) {
    return (
      <div
        style={{ transform: `translate3d(${offset.x}px, ${offset.y}px, 0)` }}
        onPointerDown={handlePointerDown}
        onClick={() => setIsExpanded(true)}
        className="mx-auto mt-1.5 z-30 cursor-pointer select-none transition-transform duration-200"
        title="Tap to expand JARVIS Dynamic Island"
      >
        <div
          style={{
            borderColor: `${customTheme.primaryHex}66`,
            boxShadow: `0 8px 28px rgba(0,0,0,0.75), 0 0 18px ${customTheme.primaryHex}33`,
          }}
          className={`liquid-glass-pill rounded-full ${pillWidthClass} border flex items-center gap-2.5`}
        >
          <div className="relative w-5 h-5 rounded-full flex items-center justify-center overflow-hidden shrink-0">
            <div className="absolute inset-0 siri-orb-glow opacity-90" />
            <div className="relative w-2.5 h-2.5 rounded-full bg-slate-950" />
          </div>

          <span className="text-[11px] font-semibold text-white tracking-tight whitespace-nowrap">
            {activeTaskTitle
              ? `${activeTaskTitle} — Step ${currentStepNumber} of ${totalSteps}`
              : 'JARVIS ULTRA · Say "Hey Jarvis"'}
          </span>

          <div className="flex items-center gap-0.5 h-3" aria-hidden="true">
            {waveformBars.slice(0, 5).map((h, i) => (
              <div
                key={i}
                className="w-0.5 rounded-full transition-transform duration-150"
                style={{
                  height: '10px',
                  backgroundColor: customTheme.primaryHex,
                  transform: `scaleY(${Math.max(0.25, h / 100)})`,
                }}
              />
            ))}
          </div>
        </div>
      </div>
    );
  }

  const stateBadgeLabel = activeTaskTitle
    ? isGuidancePaused
      ? 'Paused Navigation'
      : `Navigating · Step ${currentStepNumber}/${totalSteps}`
    : orbState === OrbVisualState.LISTENING
    ? 'Listening'
    : orbState === OrbVisualState.THINKING
    ? 'Thinking'
    : orbState === OrbVisualState.SPEAKING
    ? 'Speaking'
    : 'Ready';

  return (
    <div
      style={{ transform: `translate3d(${offset.x}px, ${offset.y}px, 0)` }}
      onPointerDown={handlePointerDown}
      className="mx-3 mt-1.5 z-30 select-none cursor-grab active:cursor-grabbing transition-opacity duration-150"
      role="dialog"
      aria-label="JARVIS ULTRA Dynamic Island"
    >
      <div
        style={{
          backdropFilter: `blur(${customTheme.glassBlurPx}px) saturate(190%)`,
          borderColor: `${customTheme.primaryHex}55`,
          boxShadow: `0 18px 44px rgba(0,0,0,0.75), 0 0 24px ${customTheme.primaryHex}28`,
        }}
        className="liquid-glass rounded-[26px] border p-3.5 text-slate-100 space-y-2.5"
      >
        {/* Header Row */}
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="relative w-8 h-8 rounded-full flex items-center justify-center overflow-hidden shrink-0">
              <div className="absolute inset-0 siri-orb-glow" />
              <div className="relative w-6 h-6 rounded-full bg-slate-950/90 flex items-center justify-center">
                {activeTaskTitle ? (
                  <Navigation
                    className="w-3.5 h-3.5"
                    style={{ color: customTheme.primaryHex }}
                  />
                ) : orbState === OrbVisualState.SPEAKING ? (
                  <Volume2
                    className="w-3.5 h-3.5"
                    style={{ color: customTheme.primaryHex }}
                  />
                ) : orbState === OrbVisualState.THINKING ? (
                  <Sparkles className="w-3.5 h-3.5 text-sky-300 animate-spin" />
                ) : (
                  <Mic
                    className="w-3.5 h-3.5"
                    style={{ color: customTheme.primaryHex }}
                  />
                )}
              </div>
            </div>

            <div className="min-w-0">
              <div className="flex items-center gap-1.5 text-xs font-bold text-white truncate">
                <span>JARVIS</span>
                <span aria-hidden="true" className="text-slate-500">
                  ·
                </span>
                <span style={{ color: customTheme.primaryHex }}>{stateBadgeLabel}</span>
                {idleSecondsRemaining !== null && !activeTaskTitle && (
                  <span className="font-mono text-[10px] text-slate-400 tabular-nums">
                    ({idleSecondsRemaining}s)
                  </span>
                )}
              </div>

              <div className="flex items-center gap-1 mt-0.5">
                <div className="flex items-center gap-0.5 h-2.5" aria-hidden="true">
                  {waveformBars.map((bar, idx) => (
                    <div
                      key={idx}
                      className="w-1 rounded-full transition-transform duration-150"
                      style={{
                        height: '10px',
                        backgroundColor: customTheme.primaryHex,
                        transform: `scaleY(${Math.max(0.22, bar / 100)})`,
                      }}
                    />
                  ))}
                </div>
                <span className="text-[10px] text-slate-300 ml-1 truncate">
                  {activeTaskTitle
                    ? `${activeTaskTitle} — Step ${currentStepNumber} of ${totalSteps}`
                    : cardData?.languageLabel || 'Omni AI'}
                </span>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-1 shrink-0">
            <button
              type="button"
              onClick={() => setIsExpanded(false)}
              className="p-1.5 rounded-full text-slate-300 hover:text-white hover:bg-white/10 transition-colors"
              title="Collapse to Compact Pill"
            >
              <Minimize2 className="w-3.5 h-3.5" />
            </button>
            <button
              type="button"
              onClick={onDismissIsland}
              className="p-1.5 rounded-full text-slate-300 hover:text-white hover:bg-white/10 transition-colors"
              title="Minimize Overlay"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        {/* Active Live Line Navigation Transport Controls (Step 1..N) */}
        {activeTaskTitle && (
          <div className="rounded-2xl bg-slate-950/85 border border-white/15 p-2.5 space-y-2">
            <div className="flex items-center justify-between text-[11px]">
              <span className="font-semibold text-white truncate">{activeTaskTitle}</span>
              <span
                className="font-mono font-bold shrink-0"
                style={{ color: customTheme.primaryHex }}
              >
                Step {currentStepNumber} of {totalSteps}
              </span>
            </div>

            <p className="text-xs text-slate-200 leading-snug">{jarvisResponse}</p>

            {/* Step Navigation Controls: Prev, Pause/Resume, Next, Repeat, Stop */}
            <div className="flex items-center justify-between gap-1 pt-1 border-t border-white/10">
              <button
                type="button"
                onClick={onPrevStep}
                disabled={currentStepNumber <= 1}
                className="px-2 py-1 rounded-lg bg-white/10 hover:bg-white/15 disabled:opacity-40 text-[10px] font-medium text-white flex items-center gap-1"
                title="Previous Step"
              >
                <SkipBack className="w-3 h-3" />
                <span>Prev</span>
              </button>

              <button
                type="button"
                onClick={onPauseResumeGuidance}
                className="px-2.5 py-1 rounded-lg bg-white/10 hover:bg-white/15 text-[10px] font-medium text-white flex items-center gap-1"
              >
                {isGuidancePaused ? (
                  <>
                    <Play className="w-3 h-3 text-emerald-300" />
                    <span>Resume</span>
                  </>
                ) : (
                  <>
                    <Pause className="w-3 h-3 text-amber-300" />
                    <span>Pause</span>
                  </>
                )}
              </button>

              <button
                type="button"
                onClick={onNextStep}
                className="px-2.5 py-1 rounded-lg text-[10px] font-bold text-slate-950 flex items-center gap-1"
                style={{ backgroundColor: customTheme.primaryHex }}
              >
                <span>Next Step</span>
                <SkipForward className="w-3 h-3" />
              </button>

              <button
                type="button"
                onClick={onRepeatStep}
                className="p-1.5 rounded-lg bg-white/10 hover:bg-white/15 text-slate-200"
                title="Repeat Voice Instruction"
              >
                <RotateCcw className="w-3 h-3" />
              </button>

              <button
                type="button"
                onClick={onStopGuidance}
                className="p-1.5 rounded-lg bg-rose-500/20 hover:bg-rose-500/30 text-rose-300"
                title="Stop Navigation"
              >
                <Square className="w-3 h-3" />
              </button>
            </div>
          </div>
        )}

        {/* Standard Conversational Transcript & Output when not in multi-step navigation */}
        {!activeTaskTitle && (
          <>
            {userTranscript && (
              <div className="text-[11px] text-cyan-200/90 font-medium bg-white/5 rounded-xl px-2.5 py-1 border border-white/10 truncate">
                &ldquo;{userTranscript}&rdquo;
              </div>
            )}

            {jarvisResponse && (
              <p className="text-xs text-slate-100 leading-relaxed line-clamp-3">
                {jarvisResponse}
              </p>
            )}
          </>
        )}

        {/* On-Demand App Permission Prompt */}
        {cardData?.requiresAppPermissionId && (
          <div className="rounded-2xl bg-slate-950/90 border border-cyan-400/40 p-2.5 space-y-2">
            <div className="flex items-start gap-2">
              <Lock className="w-3.5 h-3.5 text-cyan-300 shrink-0 mt-0.5" />
              <div className="text-[11px]">
                <div className="font-semibold text-white">
                  Allow Private Access to {cardData.requiresAppPermissionName}?
                </div>
                <p className="text-[10px] text-slate-300 mt-0.5">
                  {cardData.permissionScopeDescription}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => onGrantAppPermission(cardData.requiresAppPermissionId!)}
                className="flex-1 py-1 px-2.5 rounded-xl bg-cyan-400 text-slate-950 font-semibold text-[11px] flex items-center justify-center gap-1"
              >
                <Check className="w-3 h-3" />
                <span>Allow (&ldquo;Haan&rdquo;)</span>
              </button>
              <button
                type="button"
                onClick={onDenyAppPermission}
                className="py-1 px-2.5 rounded-xl bg-white/10 text-slate-200 text-[11px]"
              >
                Deny
              </button>
            </div>
          </div>
        )}

        {/* Compact Intelligence Bullet Summary when not navigating */}
        {!activeTaskTitle &&
          cardData &&
          !cardData.requiresAppPermissionId &&
          cardData.structuredBullets.length > 0 && (
            <div className="rounded-xl bg-white/[0.04] border border-white/10 p-2.5 space-y-1">
              {cardData.actionPerformed && (
                <div className="flex items-center gap-1 text-[10px] font-semibold text-emerald-300">
                  <ShieldCheck className="w-3 h-3 shrink-0" />
                  <span className="truncate">{cardData.actionPerformed}</span>
                </div>
              )}
              <ul className="space-y-0.5 text-[10px] text-slate-300">
                {cardData.structuredBullets.slice(0, 2).map((b, i) => (
                  <li key={i} className="truncate">
                    • {b}
                  </li>
                ))}
              </ul>
            </div>
          )}
      </div>
    </div>
  );
};
