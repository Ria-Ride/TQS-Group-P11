/**
 * Ria Ride logo: icon, wordmark and tagline. The icon is a simplified drawing of the official
 * one; replace the <svg> with the designer's SVG when it is available.
 */
export function Logo() {
  return (
    <div className="logo">
      <svg className="logo-mark" viewBox="0 0 64 64" aria-hidden="true" focusable="false">
        <defs>
          <clipPath id="riaride-logo-circle">
            <circle cx="32" cy="32" r="32" />
          </clipPath>
        </defs>
        <g clipPath="url(#riaride-logo-circle)">
          <rect width="64" height="64" fill="#7FC8D6" />
          <g
            fill="none"
            stroke="#FFFFFF"
            strokeWidth="3.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <circle cx="19" cy="40" r="8" />
            <circle cx="45" cy="40" r="8" />
            <path d="M19 40 L28 30 H41 M28 30 L45 40 M37 21 L41 30 M33 21 H41" />
          </g>
          <path d="M0 41 C14 35 26 37 34 41 C44 46 54 43 64 39 V64 H0 Z" fill="#E2632B" />
          <path d="M0 51 C16 41 30 39 40 43 C50 47 58 45 64 41 V64 H0 Z" fill="#0E4F63" />
        </g>
      </svg>
      <div>
        <p className="logo-word">
          <span className="logo-ria">Ria</span>
          <span className="logo-ride">Ride</span>
        </p>
        <p className="logo-tagline" lang="pt">
          Pedala ao ritmo da ria
        </p>
      </div>
    </div>
  );
}