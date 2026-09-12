import { useState } from "react";
import { Bot, Clock, Mail, Phone } from "lucide-react";

import { usePatientPortal } from "../../../components/patient/usePatientPortal";

/** Displays patient support options, FAQs, and the AI assistant launcher. */
export function HelpSupportView() {
  const { data } = usePatientPortal();
  const faqs = data.faqs;
  const [openFaqId, setOpenFaqId] = useState<string | null>(faqs[0]?.id ?? null);

  /** Expands the selected FAQ or collapses it when already open. */
  const toggleFaq = (id: string) => {
    setOpenFaqId(openFaqId === id ? null : id);
  };

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
      {/* FAQ Accordion */}
      <section className="rounded-2xl border border-brand-line bg-white p-6 shadow-xs lg:col-span-2">
        <div className="border-b border-brand-line pb-4 mb-4">
          <h2 className="text-lg font-bold text-brand-ink">Frequently Asked Questions</h2>
          <p className="text-xs text-brand-muted mt-0.5">
            Quick answers to common questions about scheduling, lab turnaround, and billing.
          </p>
        </div>

        <div className="divide-y divide-brand-line">
          {faqs.map((faq) => {
            const isOpen = openFaqId === faq.id;
            return (
              <div key={faq.id} className="py-3.5">
                <button
                  type="button"
                  onClick={() => toggleFaq(faq.id)}
                  className="flex w-full items-center justify-between text-left text-sm font-semibold text-brand-ink hover:text-brand-primary-dark cursor-pointer group"
                >
                  <span className="pr-4">{faq.question}</span>
                  <span className="font-mono text-lg text-brand-primary font-normal transition-transform group-hover:scale-110">
                    {isOpen ? "−" : "+"}
                  </span>
                </button>
                {isOpen && (
                  <p className="mt-2.5 text-xs text-brand-muted leading-relaxed animate-in fade-in slide-in-from-top-1">
                    {faq.answer}
                  </p>
                )}
              </div>
            );
          })}
        </div>
      </section>

      {/* Support Contact Panel */}
      <section className="space-y-4">
        <div className="rounded-2xl border border-brand-line bg-white p-6 shadow-xs">
          <div className="border-b border-brand-line pb-4 mb-4">
            <h2 className="text-lg font-bold text-brand-ink">Contact Care Support</h2>
            <p className="text-xs text-brand-muted mt-0.5">
              We&apos;re here around the clock to help with appointments and queries.
            </p>
          </div>

          <div className="space-y-3.5 text-xs">
            <div className="flex items-center gap-3 p-2 rounded-xl hover:bg-brand-paper transition">
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-primary-tint text-brand-primary-dark shrink-0">
                <Phone className="h-4 w-4" />
              </span>
              <div>
                <p className="font-semibold text-brand-ink">Helpline (24/7)</p>
                <p className="font-brand-mono text-brand-muted mt-0.5">+91 11 4567 8900</p>
              </div>
            </div>

            <div className="flex items-center gap-3 p-2 rounded-xl hover:bg-brand-paper transition">
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-amber-tint text-brand-amber shrink-0">
                <Mail className="h-4 w-4" />
              </span>
              <div>
                <p className="font-semibold text-brand-ink">Email Support</p>
                <p className="text-brand-muted mt-0.5">support@medops.example</p>
              </div>
            </div>

            <div className="flex items-center gap-3 p-2 rounded-xl hover:bg-brand-paper transition">
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-primary-tint text-brand-primary-dark shrink-0">
                <Clock className="h-4 w-4" />
              </span>
              <div>
                <p className="font-semibold text-brand-ink">Operating Hours</p>
                <p className="text-brand-muted mt-0.5">Mon–Sat, 8:00 AM – 8:00 PM IST</p>
              </div>
            </div>
          </div>
        </div>

        {/* AI Assistant Section */}
        <div className="rounded-2xl border border-brand-line bg-white p-6 shadow-xs">
          <div className="border-b border-brand-line pb-4 mb-4">
            <h2 className="text-lg font-bold text-brand-ink">Get Instant Help with MedOps</h2>
            <p className="text-xs text-brand-muted mt-0.5">
              Try our AI assistant for quick answers about appointments, prescriptions, lab
              reports, billing, and using MedOps.
            </p>
          </div>

          <button
            type="button"
            onClick={() => {
              window.dispatchEvent(new CustomEvent("medops:open-ai-chat"));
            }}
            className="flex w-full items-center justify-center gap-2 rounded-xl bg-brand-primary py-2.5 text-xs font-semibold text-white transition hover:bg-brand-primary-dark cursor-pointer shadow-2xs"
          >
            <Bot className="h-4 w-4" />
            <span>Chat with MedOps AI</span>
          </button>
        </div>
      </section>
    </div>
  );
}
