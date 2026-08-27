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

export function LoginBrandPanel() {
  return (
    <div className="relative hidden flex-1 flex-col justify-center overflow-hidden px-16 py-12 text-white lg:flex">
      <div
        className="absolute inset-0 bg-cover bg-center"
        style={{ backgroundImage: `url(${hospitalBg})` }}
      />
      {/* Blue overlay so white text stays readable over any photo */}
      <div className="absolute inset-0 bg-gradient-to-br from-blue-900/90 via-blue-800/85 to-blue-600/70" />

      <div className="relative flex items-center gap-3">
        <MedOpsLogo className="h-10 w-10 shrink-0" />
        <div>
          <span className="text-2xl font-bold tracking-tight">MEDOPS</span>
          <p className="text-sm text-blue-200">Healthcare Management System</p>
        </div>
      </div>

      <div className="relative mt-10 h-1 w-12 rounded-full bg-blue-400" />

      <h1 className="relative mt-6 text-4xl leading-tight font-bold">
        Simplifying Healthcare,
        <br />
        Empowering Better Care
      </h1>

      <p className="relative mt-4 max-w-md text-blue-100">
        MedOps helps hospitals and clinics manage patients, appointments,
        doctors, and records efficiently in one secure platform.
      </p>

      <div className="relative mt-12 space-y-6">
        {features.map(({ icon: Icon, title, description }) => (
          <div key={title} className="flex items-start gap-4">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-white/10 ring-1 ring-white/20">
              <Icon className="h-5 w-5" />
            </div>
            <div>
              <p className="font-semibold">{title}</p>
              <p className="text-sm text-blue-200">{description}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
