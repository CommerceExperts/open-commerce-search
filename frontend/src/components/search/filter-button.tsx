import { IconFilter } from "@tabler/icons-react";
import { Button, buttonVariants } from "../ui/button";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "../ui/sheet";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "../ui/accordion";

export function FilterButton(props: React.ComponentProps<"button">) {
  return (
    <Sheet>
      <SheetTrigger
        className={buttonVariants({ variant: "outline", size: "sm" })}
        {...props}
      >
        <IconFilter />
        <span>Filter</span>
      </SheetTrigger>
      <SheetContent>
        <SheetHeader>
          <SheetTitle>Filter</SheetTitle>
          <Accordion type="single" collapsible className="w-full">
            <AccordionItem value="item-1">
              <AccordionTrigger>Category</AccordionTrigger>
              <AccordionContent>TODO</AccordionContent>
            </AccordionItem>
            <AccordionItem value="item-2">
              <AccordionTrigger>Price</AccordionTrigger>
              <AccordionContent>TODO</AccordionContent>
            </AccordionItem>
            <AccordionItem value="item-3">
              <AccordionTrigger>Color</AccordionTrigger>
              <AccordionContent>TODO</AccordionContent>
            </AccordionItem>
          </Accordion>
        </SheetHeader>
      </SheetContent>
    </Sheet>
  );
}
