import { IconInfoCircle } from "@tabler/icons-react";
import { Button } from "../ui/button";

export function DetailsButton(props: React.ComponentProps<"button">) {
  return (
    <Button variant="secondary" {...props}>
      <IconInfoCircle />
      <span className="hidden sm:block">Details</span>
    </Button>
  );
}
