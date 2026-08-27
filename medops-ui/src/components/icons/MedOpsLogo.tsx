import { useId } from "react";

interface MedOpsLogoProps {
  className?: string;
}

const HEART_PATH =
  "M72 139 C64 131 24 101 15 72 C5 42 23 18 50 18 C66 18 78 27 87 39 " +
  "C96 27 108 18 124 18 C151 18 169 42 159 72 C150 101 110 131 102 139 L87 153 Z";

const ECG_PATH = "M28 78 H54 L66 61 L80 94 L94 48 L108 78 H139";

// ECG line is a mask cutout (not a white stroke) so it works on any background color.
export function MedOpsLogo({ className }: Readonly<MedOpsLogoProps>) {
  const maskId = useId();

  return (
    <svg viewBox="0 0 205 190" fill="none" className={className} aria-hidden="true">
      <g transform="translate(18,18)">
        <mask id={maskId}>
          <path d={HEART_PATH} fill="white" />
          <path
            d={ECG_PATH}
            fill="none"
            stroke="black"
            strokeWidth="10"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </mask>
        <path d={HEART_PATH} fill="currentColor" mask={`url(#${maskId})`} />
      </g>
    </svg>
  );
}
