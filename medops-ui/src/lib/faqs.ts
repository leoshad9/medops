import type { FaqItem } from "../types/patient";

/**
 * Static, deployment-wide help content.
 *
 * Previously embedded in mockDashboardData.ts as placeholder data; FAQs are
 * not per-patient records, so they live here as app configuration instead.
 */
export const patientFaqs: FaqItem[] = [
  {
    id: "faq-1",
    question: "How do I reschedule an appointment?",
    answer:
      'Go to Appointments, find the visit, and select "Reschedule / Cancel." You can choose a new date and time or cancel outright.',
  },
  {
    id: "faq-2",
    question: "How long does it take to get lab results?",
    answer:
      'Most results are available within 24–48 hours and appear under Lab Reports with a "New" label once ready.',
  },
  {
    id: "faq-3",
    question: "Can I request a prescription refill online?",
    answer:
      'Yes — open Prescriptions and select "Request Refill" on the medication you need. Your doctor will review and approve it.',
  },
  {
    id: "faq-4",
    question: "How do I update my insurance information?",
    answer:
      'Visit My Profile and select "Edit Profile" to update your insurance provider and policy details.',
  },
];
