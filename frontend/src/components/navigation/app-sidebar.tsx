import * as React from "react";

import { NavGeneral } from "@/components/navigation/nav-general";
import { NavUser } from "@/components/navigation/nav-user";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar";
import { NavConfiguration } from "./nav-configuration";

export function AppSidebar({ ...props }: React.ComponentProps<typeof Sidebar>) {
  return (
    <Sidebar collapsible="offcanvas" {...props}>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton asChild className="py-7 px-0">
              <a href="#">
                <div className="flex aspect-square size-10 items-center justify-center rounded-lg">
                  <img src="/favicon.ico" alt="OCSS logo" />
                </div>
                <div className="grid flex-1 text-left text-sm leading-tight">
                  <span className="truncate font-semibold">OCSS</span>
                  <span className="truncate text-xs">
                    Open Commerce Search Stack
                  </span>
                </div>
              </a>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <NavGeneral />
        <NavConfiguration />
      </SidebarContent>
      <SidebarFooter>
        <NavUser
          user={{
            name: "Max Mustermann",
            email: "max.m@gmail.com",
            avatar: "",
          }}
        />
      </SidebarFooter>
    </Sidebar>
  );
}
