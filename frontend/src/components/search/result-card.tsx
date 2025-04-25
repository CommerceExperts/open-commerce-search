import { IconInfoCircle, IconCrown, IconTrash } from "@tabler/icons-react";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "../ui/card";
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuTrigger,
} from "../ui/context-menu";

export function ResultCard() {
  return (
    <ContextMenu>
      <ContextMenuTrigger className="cursor-pointer w-[300px]">
        <Card className="pt-0 overflow-hidden group">
          <img
            className="w-full h-[200px] object-cover group-hover:opacity-90"
            src="https://lh6.googleusercontent.com/bvausm7YIKnxWXGb2HPKxVxDTeI_TU7tFZ1cniUFFbqKHavcCHbrFxEiU1MW6p0Mz5r3V0e-S3_UHxTlPxlD6vDjD0iu9NA1DUUN0FrNgytYm0C8SKgIp1Ncgny33vh9o9x-SsALR3-igp5EFtwTjz9Bet_54eaZzvVGqa8IcvJWC2nprlwviXlptSv9fQ"
          />
          <CardHeader>
            <CardTitle>Card Title</CardTitle>
            <CardDescription>Card Description</CardDescription>
          </CardHeader>
          <CardContent>
            <p>Card Content</p>
          </CardContent>
        </Card>
      </ContextMenuTrigger>
      <ContextMenuContent>
        <ContextMenuItem>
          <IconInfoCircle />
          <span>Details</span>
        </ContextMenuItem>
        <ContextMenuItem>
          <IconCrown />
          <span>Highlight</span>
        </ContextMenuItem>
        <ContextMenuItem>
          <IconTrash />
          <span>Delete</span>
        </ContextMenuItem>
      </ContextMenuContent>
    </ContextMenu>
  );
}
