import { IconSettings } from "@tabler/icons-react";
import { Button } from "../ui/button";

export function SettingsButton(props: React.ComponentProps<"button">) {
  return (
    <Button variant="default" {...props}>
      <IconSettings />
      <span>Settings</span>
    </Button>
  );
}
