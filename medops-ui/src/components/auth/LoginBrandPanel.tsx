import { BarChart3, ShieldCheck, Users } from "lucide-react";
import hospitalBg from "../../assets/images/hospital-bg.png";
import { MedOpsLogo } from "../icons/MedOpsLogo";

const features = [
  {
    icon: ShieldCheck,
    title: "Secure",
    description: "Your data is protected with enterprise-grade security.",
  },
  {
    icon: Users,
    title: "Efficient",
    description: "Streamline operations and save valuable time.",
  },
  {
    icon: BarChart3,
    title: "Reliable",
    description: "Built for healthcare professionals you can rely on.",
  },
];

export function LoginBrandPanel({ className = '' }) {
  return (
    <div className={`relative hidden flex-1 flex-col justify-center overflow-hidden px-8 sm:px-10 md:px-12 lg:px-14 xl:px-16 py-10 sm:py-12 lg:flex ${className}`}>
      <div
        className="absolute inset-0 bg-cover bg-center"
        style={{ backgroundImage: `url(${hospitalBg})` }}
      />
      {/* Green brand overlay so white text stays readable over any photo */}
      <div className="absolute inset-0 bg-gradient-to-br from-brand-ink/95 via-brand-primary-dark/90 to-brand-primary/75" />

      <div className="relative flex items-center gap-3">
        <MedOpsLogo className="h-10 w-10 shrink-0 text-white" />
        <div>
          <span className="text-xl sm:text-2xl font-bold tracking-tight text-white">MEDOPS</span>
          <p className="text-xs sm:text-sm text-brand-primary-tint/90">Healthcare Management System</p>
        </div>
      </div>

      <div className="relative mt-6 sm:mt-10 h-1 w-10 sm:w-12 rounded-full bg-brand-primary-tint" />

      <h1 className="relative mt-4 sm:mt-6 text-2xl sm:text-3xl lg:text-4xl leading-tight font-bold text-white">
        Simplifying Healthcare,
        <br />
        Empowering Better Care
      </h1>

      <p className="relative mt-3 sm:mt-4 max-w-md text-sm sm:text-base text-brand-primary-tint/95">
        MedOps helps hospitals and clinics manage patients, appointments,
        doctors, and records efficiently in one secure platform.
      </p>

      <div className="relative mt-8 sm:mt-12 space-y-5 sm:space-y-6">
        {features.map(({ icon: Icon, title, description }) => (
          <div key={title} className="flex items-start gap-3 sm:gap-4">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-white/20 bg-white/[0.12]">
              <Icon className="h-5 w-5 text-white" />
            </div>
            <div>
              <p className="font-semibold text-sm sm:text-base text-white">{title}</p>
              <p className="text-xs sm:text-sm text-brand-primary-tint/85">{description}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
