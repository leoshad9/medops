import { useState } from "react";
import { Clock, Mail, MessageSquare, Phone } from "lucide-react";

import { usePatientPortal } from "../../../components/patient/usePatientPortal";

export function HelpSupportView() {
  const { data } = usePatientPortal();
  const faqs = data.faqs;
  const [openFaqId, setOpenFaqId] = useState<string | null>(faqs[0]?.id ?? null);
  const [chatActive, setChatActive] = useState(false);

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

          <button
            type="button"
            onClick={() => setChatActive(true)}
            className="mt-6 flex w-full items-center justify-center gap-2 rounded-xl bg-brand-primary py-2.5 text-xs font-semibold text-white transition hover:bg-brand-primary-dark cursor-pointer shadow-2xs"
          >
            <MessageSquare className="h-4 w-4" />
            <span>Start Live Chat</span>
          </button>
        </div>

        {/* Live Chat Modal */}
        {chatActive && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-brand-ink/40 p-4 backdrop-blur-xs">
            <div className="w-full max-w-md rounded-2xl border border-brand-line bg-white p-6 shadow-xl animate-in fade-in zoom-in-95">
              <div className="flex items-center justify-between border-b border-brand-line pb-3">
                <div className="flex items-center gap-2">
                  <span className="h-2.5 w-2.5 rounded-full bg-brand-success animate-pulse" />
                  <h3 className="text-sm font-bold text-brand-ink">MedOps Live Patient Support</h3>
                </div>
                <button
                  type="button"
                  onClick={() => setChatActive(false)}
                  className="text-xs text-brand-muted hover:text-brand-ink cursor-pointer"
                >
                  ✕
                </button>
              </div>

              <div className="my-4 h-48 rounded-xl bg-brand-paper p-3 text-xs space-y-2 overflow-y-auto border border-brand-line">
                <div className="bg-white p-2.5 rounded-lg shadow-2xs max-w-[85%] border border-brand-line">
                  <p className="font-semibold text-brand-primary-dark text-[11px]">MedOps Support Agent</p>
                  <p className="text-brand-ink mt-0.5">Hello John! How can our patient care team help you today?</p>
                </div>
              </div>

              <div className="flex gap-2">
                <input
                  type="text"
                  placeholder="Type your message..."
                  className="flex-1 rounded-xl border border-brand-line px-3 py-2 text-xs text-brand-ink focus:border-brand-primary focus:outline-hidden"
                />
                <button
                  type="button"
                  onClick={() => alert("Message sent to care agent.")}
                  className="rounded-xl bg-brand-primary px-3.5 py-2 text-xs font-semibold text-white hover:bg-brand-primary-dark cursor-pointer"
                >
                  Send
                </button>
              </div>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
