// Date.getHours() returns local time in whatever timezone the browser/OS is
// set to, so this is already timezone-correct for the viewer without any
// extra timezone handling.
export function getTimeOfDayGreeting(date: Date = new Date()): string {
  const hour = date.getHours();
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}
