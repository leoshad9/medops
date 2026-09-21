import { NotificationDropdown } from "../common/NotificationDropdown";
import type { NotificationDropdownProps } from "../common/NotificationDropdown";
import { DOCTOR_PATHS } from "../../lib/doctorRoutes";

export { NotificationDropdown };

export function DoctorNotificationDropdown(
    props: Omit<NotificationDropdownProps, "viewAllPath">
) {
    return (
        <NotificationDropdown
            {...props}
            viewAllPath={DOCTOR_PATHS.notifications}
            markAllIconSize="h-3"
        />
    );
}
