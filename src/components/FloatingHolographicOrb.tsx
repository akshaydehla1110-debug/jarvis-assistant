import React, { useState, useRef, useEffect } from 'react';
import { Mic, Volume2, CheckCircle2, AlertTriangle, X, Pause, Maximize2, ShieldAlert } from 'lucide-react';
import { OrbVisualState, PendingConfirmation } from '../types/jarvis';

interface FloatingHolographicOrbProps {
  orbState: OrbVisualState;
  transcript: string;
  subtext: string;
  waveformBars: number[];
  pendingConfirmation: PendingConfirmation | null;
  isLockScreen: boolean;
  idleSecondsRemaining: number | null;
  onTapPauseOrDismiss: () => void;
  onOpenFullAssistant: () => void;
  onConfirmAction: () => void;
  onCancelAction: () => void;
}

export const FloatingHolographicOrb: React.FC<FloatingHolographicOrbProps> = ({
  orbState,
  transcript,
  subtext,
  waveformBars,
  pendingConfirmation,
  isLockScreen,
  idleSecondsRemaining,
  onTapPauseOrDismiss,
  onOpenFullAssistant,
  onConfirmAction,
  onCancelAction,
}) => {
  const [position, setPosition] = useState<{ x: number; y: number }>({ x: 16, y: 78 });
  const [isDragging, setIsDragging] = useState(false);
  const dragStartRef = useRef<{ clientX: number; clientY: number; startX: number; startY: number } | null>(null);

  useEffect(() => {
    if (!isDragging) return;

    const handlePointerMove = (e: PointerEvent) => {
      if (!dragStartRef.current) return;
      const dx = e.clientX - dragStartRef.current.clientX;
      const dy = e.clientY - dragStartRef.current.clientY;
      setPosition({
        x: Math.max(8, Math.min(180, dragStartRef.current.startX + dx)),
        y: Math.max(52, Math.min(480, dragStartRef.current.startY + dy)),
      });
    };

    const handlePointerUp = () => {
      setIsDragging(false);
      dragStartRef.current = null;
    };

    window.addEventListener('pointermove', handlePointerMove);
    window.addEventListener('pointerup', handlePointerUp);
    return () => {
      window.removeEventListener('pointermove', handlePointerMove);
      window.removeEventListener('pointerup', handlePointerUp);
    };
  }, [isDragging]);

  if (orbState === OrbVisualState.HIDDEN) {
    return null;
  }

  const handlePointerDown = (e: React.PointerEvent) => {
    if ((e.target as HTMLElement).closest('button')) return;
    setIsDragging(true);
    dragStartRef.current = {
      clientX: e.clientX,
      clientY: e.clientY,
      startX: position.x,
      startY: position.y,
    };
  };

  const stateConfig: Record<
    Exclude<OrbVisualState, OrbVisualState.HIDDEN>,
    { label: string; ringColor: string; glowColor: string; icon: React.ReactNode }
  > = {
    [OrbVisualState.LISTENING]: {
      label: 'Listening',
      ringColor: 'border-cyan-400',
      glowColor: 'shadow-cyan-500/30',
      icon: <Mic className="w-4 h-4 text-cyan-300" />,
    },
    [OrbVisualState.THINKING]: {
      label: 'Validating',
      ringColor: 'border-sky-400',
      glowColor: 'shadow-sky-500/30',
      icon: <div className="w-3.5 h-3.5 border-2 border-sky-300 border-t-transparent rounded-full animate-spin" />,
    },
    [OrbVisualState.SPEAKING]: {
      label: 'Speaking (TTS)',
      ringColor: 'border-cyan-300',
      glowColor: 'shadow-cyan-400/40',
      icon: <Volume2 className="w-4 h-4 text-cyan-200" />,
    },
    [OrbVisualState.CONFIRMATION]: {
      label: 'Confirm Action',
      ringColor: 'border-amber-400',
      glowColor: 'shadow-amber-500/30',
      icon: <ShieldAlert className="w-4 h-4 text-amber-300" />,
    },
    [OrbVisualState.COMPLETED]: {
      label: 'Completed',
      ringColor: 'border-emerald-400',
      glowColor: 'shadow-emerald-500/30',
      icon: <CheckCircle2 className="w-4 h-4 text-emerald-300" />,
    },
    [OrbVisualState.ERROR]: {
      label: 'Blocked / Error',
      ringColor: 'border-rose-400',
      glowColor: 'shadow-rose-500/30',
      icon: <AlertTriangle className="w-4 h-4 text-rose-300" />,
    },
  };

  const currentVisual = stateConfig[orbState];

  // Lock screen privacy enforcement: never expose sensitive note/message content over secure lock screen
  const displayTranscript =
    isLockScreen && orbState === OrbVisualState.CONFIRMATION
      ? 'Device locked. Unlock required for sensitive preview.'
      : transcript;

  return (
    <div
      onPointerDown={handlePointerDown}
      style={{
        transform: `translate3d(${position.x}px, ${position.y}px, 0)`,
      }}
      className="absolute top-0 left-0 z-30 w-[312px] select-none cursor-grab active:cursor-grabbing transition-opacity duration-150"
      role="dialog"
      aria-label="JARVIS Floating Holographic Overlay"
    >
      <div
        className={`rounded-2xl bg-slate-950/90 backdrop-blur-md border ${currentVisual.ringColor}/60 shadow-xl ${currentVisual.glowColor} p-3.5 text-slate-100`}
      >
        {/* Top Row: Holographic Cyan Orb + Subtle Waveform + Quick Non-Blocking Controls */}
        <div className="flex items-center justify-between gap-2.5">
          <div className="flex items-center gap-2.5 min-w-0">
            {/* Cyan Holographic Core */}
            <div
              className={`relative w-10 h-10 rounded-full flex items-center justify-center bg-cyan-950/80 border ${currentVisual.ringColor} shadow-inner shrink-0`}
            >
              <div className="absolute inset-1 rounded-full border border-cyan-400/30" />
              {currentVisual.icon}
            </div>

            <div className="min-w-0">
              <div className="flex items-center gap-1.5 text-xs font-semibold text-cyan-300 tracking-tight">
                <span>JARVIS</span>
                <span aria-hidden="true">·</span>
                <span>{currentVisual.label}</span>
                {idleSecondsRemaining !== null && (
                  <>
                    <span aria-hidden="true">·</span>
                    <span className="font-mono text-[11px] text-slate-400 tabular-nums">
                      {idleSecondsRemaining}s
                    </span>
                  </>
                )}
              </div>

              {/* Subtle Animated Waveform */}
              <div className="flex items-center gap-1 h-4 mt-0.5" aria-hidden="true">
                {waveformBars.map((heightPct, idx) => (
                  <div
                    key={idx}
                    className="w-1 rounded-full bg-cyan-400/80 transition-transform duration-150"
                    style={{
                      height: '12px',
                      transform: `scaleY(${Math.max(0.2, heightPct / 100)})`,
                    }}
                  />
                ))}
                <span className="text-[11px] text-slate-400 truncate ml-1">{subtext}</span>
              </div>
            </div>
          </div>

          {/* Interactive Hitboxes: Tap to Pause/Open/Dismiss */}
          <div className="flex items-center gap-1 shrink-0">
            <button
              type="button"
              onClick={onOpenFullAssistant}
              title="Open JARVIS Setup Activity"
              className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-slate-800/70 transition-colors"
            >
              <Maximize2 className="w-3.5 h-3.5" />
            </button>
            <button
              type="button"
              onClick={onTapPauseOrDismiss}
              title="Pause and Dismiss Overlay"
              className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-slate-800/70 transition-colors"
            >
              {orbState === OrbVisualState.LISTENING ? (
                <Pause className="w-3.5 h-3.5" />
              ) : (
                <X className="w-3.5 h-3.5" />
              )}
            </button>
          </div>
        </div>

        {/* Transcript / Output Preview */}
        <p className="mt-2.5 text-xs text-slate-200 leading-relaxed break-words">
          {displayTranscript}
        </p>

        {/* Consequential Action Explicit Confirmation Bar */}
        {pendingConfirmation && !isLockScreen && (
          <div className="mt-3 pt-2.5 border-t border-slate-800/90 space-y-2">
            <div className="text-[11px] text-amber-200/90 font-mono">
              <div>{pendingConfirmation.actionSummary}</div>
              <div className="text-slate-400 mt-0.5">{pendingConfirmation.previewPayload}</div>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={onConfirmAction}
                className="flex-1 py-1.5 px-3 rounded-lg bg-cyan-500 text-slate-950 font-semibold text-xs hover:bg-cyan-400 transition-colors whitespace-nowrap"
              >
                Confirm ("Yes")
              </button>
              <button
                type="button"
                onClick={onCancelAction}
                className="flex-1 py-1.5 px-3 rounded-lg bg-slate-800 text-slate-200 font-medium text-xs hover:bg-slate-700 transition-colors whitespace-nowrap"
              >
                Cancel ("No")
              </button>
            </div>
          </div>
        )}

        <div className="mt-2 pt-1.5 border-t border-slate-800/60 flex items-center justify-between text-[10px] text-slate-400">
          <span>TYPE_APPLICATION_OVERLAY · Drag to move</span>
          <span>FLAG_NOT_FOCUSABLE</span>
        </div>
      </div>
    </div>
  );
};
