import React, { useEffect, useState } from 'react';
import { AlertCircle, CheckCircle2, Hand } from 'lucide-react';

export interface GuidedStepItem {
  stepNumber: number;
  shortInstruction: string;
  voiceInstruction: string;
  targetElementId: string;
}

interface LiveScreenLineNavigationOverlayProps {
  containerRef: React.RefObject<HTMLDivElement | null>;
  isActive: boolean;
  isPaused: boolean;
  taskTitle: string;
  currentStepIndex: number;
  steps: GuidedStepItem[];
  neonColorHex: string;
  secondaryColorHex: string;
  onManualTargetSelected: (label: string) => void;
}

interface MeasuredCoords {
  originX: number;
  originY: number;
  targetX: number;
  targetY: number;
  targetLeft: number;
  targetTop: number;
  targetWidth: number;
  targetHeight: number;
  foundReliably: boolean;
}

export const LiveScreenLineNavigationOverlay: React.FC<
  LiveScreenLineNavigationOverlayProps
> = ({
  containerRef,
  isActive,
  isPaused,
  taskTitle,
  currentStepIndex,
  steps,
  neonColorHex,
  secondaryColorHex,
  onManualTargetSelected,
}) => {
  const [coords, setCoords] = useState<MeasuredCoords | null>(null);
  const currentStep = steps[currentStepIndex];

  useEffect(() => {
    if (!isActive || isPaused || !currentStep || !containerRef.current) {
      setCoords(null);
      return;
    }

    const measureTarget = () => {
      const container = containerRef.current;
      if (!container) return;
      const containerRect = container.getBoundingClientRect();

      // Dynamic Island bottom-center anchor inside the phone screen
      const originX = containerRect.width / 2;
      const originY = 98;

      if (
        !currentStep.targetElementId ||
        currentStep.targetElementId === 'MANUAL_USER_SELECT'
      ) {
        setCoords({
          originX,
          originY,
          targetX: originX,
          targetY: originY + 120,
          targetLeft: 0,
          targetTop: 0,
          targetWidth: 0,
          targetHeight: 0,
          foundReliably: false,
        });
        return;
      }

      const targetEl = container.querySelector(
        `[data-nav-id="${currentStep.targetElementId}"]`
      ) as HTMLElement | null;

      if (!targetEl) {
        // Honest Fallback: Do NOT invent fake coordinates if target UI node is not on screen
        setCoords({
          originX,
          originY,
          targetX: originX,
          targetY: originY + 120,
          targetLeft: 0,
          targetTop: 0,
          targetWidth: 0,
          targetHeight: 0,
          foundReliably: false,
        });
        return;
      }

      const targetRect = targetEl.getBoundingClientRect();
      const targetLeft = targetRect.left - containerRect.left;
      const targetTop = targetRect.top - containerRect.top;
      const targetWidth = targetRect.width;
      const targetHeight = targetRect.height;

      setCoords({
        originX,
        originY,
        targetX: targetLeft + targetWidth / 2,
        targetY: targetTop,
        targetLeft,
        targetTop,
        targetWidth,
        targetHeight,
        foundReliably: true,
      });
    };

    measureTarget();
    const timer = window.setInterval(measureTarget, 280);
    window.addEventListener('resize', measureTarget);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener('resize', measureTarget);
    };
  }, [containerRef, currentStep, isActive, isPaused]);

  if (!isActive || isPaused || !currentStep || !coords) {
    return null;
  }

  // Honest fallback banner when target element cannot be reliably detected
  if (!coords.foundReliably) {
    return (
      <div className="absolute inset-x-4 top-28 z-20 pointer-events-auto">
        <div
          className="rounded-2xl bg-slate-950/95 backdrop-blur-xl p-3.5 shadow-2xl border"
          style={{ borderColor: neonColorHex }}
        >
          <div className="flex items-start gap-2.5">
            <AlertCircle
              className="w-4 h-4 shrink-0 mt-0.5"
              style={{ color: neonColorHex }}
            />
            <div className="text-xs space-y-1">
              <div className="font-bold text-white">
                Step {currentStep.stepNumber} of {steps.length}:{' '}
                {currentStep.shortInstruction}
              </div>
              <p className="text-[11px] text-slate-300 leading-snug">
                {currentStep.voiceInstruction}
              </p>
              <div className="text-[10px] font-mono text-amber-300 pt-1">
                Target UI node not automatically exposed on this screen — No fake path drawn. Tap the target control manually or select below:
              </div>
              <div className="pt-1.5 flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => onManualTargetSelected(currentStep.shortInstruction)}
                  className="px-3 py-1 rounded-lg text-[11px] font-semibold text-slate-950 flex items-center gap-1"
                  style={{ backgroundColor: neonColorHex }}
                >
                  <Hand className="w-3 h-3" />
                  <span>Mark Step Completed Manually</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // Calculate smooth cubic Bezier curve from Dynamic Island to target element
  const startX = coords.originX;
  const startY = coords.originY;
  const endX = coords.targetX;
  const endY = Math.max(startY + 24, coords.targetY - 6);

  const cp1X = startX;
  const cp1Y = startY + (endY - startY) * 0.42;
  const cp2X = endX;
  const cp2Y = startY + (endY - startY) * 0.72;

  const pathData = `M ${startX} ${startY} C ${cp1X} ${cp1Y}, ${cp2X} ${cp2Y}, ${endX} ${endY}`;

  return (
    <div
      className="absolute inset-0 z-20 pointer-events-none overflow-hidden"
      aria-label={`Live Line Navigation Step ${currentStep.stepNumber}`}
    >
      <svg className="w-full h-full">
        <defs>
          <linearGradient id="jarvisLineGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor={neonColorHex} />
            <stop offset="100%" stopColor={secondaryColorHex} />
          </linearGradient>
          <filter id="jarvisNeonGlow" x="-30%" y="-30%" width="160%" height="160%">
            <feGaussianBlur stdDeviation="4" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        {/* Outer Soft Neon Glow Path */}
        <path
          d={pathData}
          fill="none"
          stroke={neonColorHex}
          strokeWidth="7"
          strokeOpacity="0.28"
          strokeLinecap="round"
        />

        {/* Animated Dashed Laser Path */}
        <path
          id="jarvisActiveGuidancePath"
          d={pathData}
          fill="none"
          stroke="url(#jarvisLineGrad)"
          strokeWidth="3"
          strokeDasharray="8 5"
          strokeLinecap="round"
          filter="url(#jarvisNeonGlow)"
        />

        {/* Moving Glowing Photon Dot Traveling Along the Path */}
        <circle r="6" fill="#FFFFFF" filter="url(#jarvisNeonGlow)">
          <animateMotion
            dur="1.35s"
            repeatCount="indefinite"
            path={pathData}
          />
        </circle>
        <circle r="11" fill={neonColorHex} fillOpacity="0.35">
          <animateMotion
            dur="1.35s"
            repeatCount="indefinite"
            path={pathData}
          />
        </circle>

        {/* Directional Arrowhead Pointing at the Target Control */}
        <polygon
          points={`${endX},${endY + 4} ${endX - 7},${endY - 8} ${endX + 7},${endY - 8}`}
          fill={neonColorHex}
          filter="url(#jarvisNeonGlow)"
        />

        {/* Pulsing Target Element Highlight Box */}
        <rect
          x={coords.targetLeft - 5}
          y={coords.targetTop - 5}
          width={coords.targetWidth + 10}
          height={coords.targetHeight + 10}
          rx="14"
          fill={neonColorHex}
          fillOpacity="0.08"
          stroke={neonColorHex}
          strokeWidth="2.5"
          filter="url(#jarvisNeonGlow)"
        />
      </svg>

      {/* Numbered Step Instruction Badge Floating Above the Target */}
      <div
        style={{
          transform: `translate3d(${Math.max(
            12,
            Math.min(180, coords.targetLeft)
          )}px, ${Math.max(105, coords.targetTop - 36)}px, 0)`,
        }}
        className="absolute top-0 left-0 pointer-events-none"
      >
        <div
          className="px-3 py-1 rounded-full bg-slate-950/95 text-white text-[11px] font-bold shadow-xl flex items-center gap-1.5 border whitespace-nowrap"
          style={{ borderColor: neonColorHex }}
        >
          <span
            className="w-4 h-4 rounded-full text-[10px] text-slate-950 font-extrabold flex items-center justify-center"
            style={{ backgroundColor: neonColorHex }}
          >
            {currentStep.stepNumber}
          </span>
          <span>
            Step {currentStep.stepNumber}: {currentStep.shortInstruction}
          </span>
        </div>
      </div>
    </div>
  );
};
