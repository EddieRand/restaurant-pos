// Generic order illustration: shopping bag + package box + receipt + leaf, on a soft
// halo. Deliberately store-type-neutral (no coffee/food-specific imagery) so it suits
// restaurants, cafés, bakeries, takeaway, and small retail alike.

interface StatusIllustrationProps {
  className?: string
}

export function StatusIllustration({ className }: StatusIllustrationProps) {
  return (
    <svg viewBox="0 0 420 320" fill="none" className={className} aria-hidden="true">
      {/* soft background halos */}
      <circle cx="210" cy="150" r="120" fill="#F4E8DC" opacity="0.55" />
      <circle cx="300" cy="110" r="64" fill="#EFE3D5" opacity="0.5" />

      {/* dotted accent */}
      <g fill="#D9A878" opacity="0.6">
        {[0, 1, 2, 3].map((r) =>
          [0, 1, 2, 3].map((c) => (
            <circle key={`${r}-${c}`} cx={70 + c * 11} cy={150 + r * 11} r="2.2" />
          )),
        )}
      </g>

      {/* ground line */}
      <line x1="70" y1="252" x2="360" y2="252" stroke="#E2D2BE" strokeWidth="3" strokeLinecap="round" />

      {/* package box */}
      <g>
        <rect x="232" y="150" width="98" height="102" rx="8" fill="#E7D3BB" />
        <rect x="232" y="150" width="98" height="34" rx="8" fill="#D9BD9B" />
        <line x1="281" y1="150" x2="281" y2="252" stroke="#C9A87E" strokeWidth="3" />
        <path d="M262 150h38v16l-19-8-19 8z" fill="#C9A87E" />
      </g>

      {/* shopping bag */}
      <g>
        <rect x="118" y="150" width="92" height="102" rx="10" fill="#EAD9C2" />
        <path d="M140 150c0-16 9-26 24-26s24 10 24 26" stroke="#B98E5E" strokeWidth="5" fill="none" strokeLinecap="round" />
        <rect x="118" y="150" width="92" height="14" rx="6" fill="#DCC4A4" />
      </g>

      {/* receipt */}
      <g>
        <path d="M176 168h70v92l-9-6-8 6-8-6-8 6-8-6-8 6-8-6-5 3z" fill="#FFFFFF" stroke="#E2D2BE" strokeWidth="2" />
        <path d="M188 186h46M188 200h46M188 214h30" stroke="#CDB89C" strokeWidth="3" strokeLinecap="round" />
      </g>

      {/* leaf sprig */}
      <g stroke="#9CAE84" strokeWidth="3" strokeLinecap="round" fill="#B7C79E">
        <path d="M338 250c0-40 12-66 40-86" fill="none" />
        <path d="M360 196c14-6 26-4 34 4-10 8-22 10-34-4z" />
        <path d="M350 220c14-6 26-4 34 4-10 8-22 10-34-4z" />
      </g>
    </svg>
  )
}
