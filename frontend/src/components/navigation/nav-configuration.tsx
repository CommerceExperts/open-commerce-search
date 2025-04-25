import {
  IconAdjustments,
  IconFilter,
  IconInputSearch,
  IconSortAscendingLetters,
  IconTool,
} from "@tabler/icons-react";

import {
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubItem,
} from "@/components/ui/sidebar";
import { Link } from "@tanstack/react-router";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";

const items = [
  {
    title: "Data Fields",
    url: "/",
    icon: IconInputSearch,
  },
  {
    title: "Filter Facets",
    url: "/",
    icon: IconFilter,
  },
  {
    title: "Sorting",
    url: "/",
    icon: IconSortAscendingLetters,
  },
];

export function NavConfiguration() {
  return (
    <SidebarGroup>
      <SidebarGroupLabel>Configuration</SidebarGroupLabel>
      <SidebarGroupContent className="flex flex-col gap-2">
        <SidebarMenu>
          {items.map((item) => (
            <SidebarMenuItem key={item.title}>
              <Link to={item.url} tabIndex={-1}>
                <SidebarMenuButton tooltip={item.title}>
                  {item.icon && <item.icon />}
                  <span>{item.title}</span>
                </SidebarMenuButton>
              </Link>
            </SidebarMenuItem>
          ))}
          <Collapsible className="group/collapsible">
            <SidebarMenuItem>
              <CollapsibleTrigger asChild>
                <SidebarMenuButton>
                  <IconAdjustments />
                  Relevance Tuning
                </SidebarMenuButton>
              </CollapsibleTrigger>
              <CollapsibleContent>
                <SidebarMenuSub>
                  <SidebarMenuSubItem>
                    <Link to="/configuration" tabIndex={-1}>
                      <SidebarMenuButton>
                        <span>Field Relevance</span>
                      </SidebarMenuButton>
                    </Link>
                  </SidebarMenuSubItem>
                  <SidebarMenuSubItem>
                    <Link to="/configuration" tabIndex={-1}>
                      <SidebarMenuButton>
                        <span>Language Knowledge</span>
                      </SidebarMenuButton>
                    </Link>
                  </SidebarMenuSubItem>
                  <SidebarMenuSubItem>
                    <Link to="/configuration" tabIndex={-1}>
                      <SidebarMenuButton>
                        <span>Scoring Rules</span>
                      </SidebarMenuButton>
                    </Link>
                  </SidebarMenuSubItem>
                </SidebarMenuSub>
              </CollapsibleContent>
            </SidebarMenuItem>
          </Collapsible>
          <Collapsible className="group/collapsible">
            <SidebarMenuItem>
              <CollapsibleTrigger asChild>
                <SidebarMenuButton>
                  <IconTool />
                  <span>Expert Configuration</span>
                </SidebarMenuButton>
              </CollapsibleTrigger>
              <CollapsibleContent>
                <SidebarMenuSub>
                  <SidebarMenuSubItem>
                    <Link to="/configuration" tabIndex={-1}>
                      <SidebarMenuButton>
                        <span>Indexing</span>
                      </SidebarMenuButton>
                    </Link>
                  </SidebarMenuSubItem>
                  <SidebarMenuSubItem>
                    <Link to="/configuration" tabIndex={-1}>
                      <SidebarMenuButton>
                        <span>Query Processing</span>
                      </SidebarMenuButton>
                    </Link>
                  </SidebarMenuSubItem>
                  <SidebarMenuSubItem>
                    <Link to="/configuration" tabIndex={-1}>
                      <SidebarMenuButton>
                        <span>Scoring Functions</span>
                      </SidebarMenuButton>
                    </Link>
                  </SidebarMenuSubItem>
                </SidebarMenuSub>
              </CollapsibleContent>
            </SidebarMenuItem>
          </Collapsible>
        </SidebarMenu>
      </SidebarGroupContent>
    </SidebarGroup>
  );
}
