import { Outlet, createRootRoute } from "@tanstack/react-router";

import { AppSidebar } from "@/components/navigation/app-sidebar";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";
import { ThemeProvider } from "@/components/ui/theme-provider";
import ConfigInitializer from "@/config/initializer";

export const Route = createRootRoute({
  component: () => (
    <ThemeProvider defaultTheme="dark" storageKey="ui-theme">
      <SidebarProvider>
        <ConfigInitializer />
        <AppSidebar variant="inset" />
        <SidebarInset>
          <Outlet />
        </SidebarInset>
      </SidebarProvider>
    </ThemeProvider>
  ),
});
