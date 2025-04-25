import { IconHistory } from "@tabler/icons-react";
import { Button } from "../ui/button";

export function HistoryButton(props: React.ComponentProps<"button">) {
  return (
    <Button variant="secondary" className="w-min" {...props}>
      <IconHistory />
      <span className="hidden lg:inline">History</span>
    </Button>
  );
}
