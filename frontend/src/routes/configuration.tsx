import { createFileRoute } from "@tanstack/react-router";
import { SiteHeader } from "@/components/navigation/site-header";
import { TenantSelect } from "@/components/configuration/tenant-select";
import { DataTable } from "@/components/configuration/data-table";
import { SaveButton } from "@/components/configuration/save-button";
import { HistoryButton } from "@/components/configuration/history-button";

export const Route = createFileRoute("/configuration")({ component: App });

function App() {
  return (
    <>
      <SiteHeader>
        <div className="w-full flex justify-between items-center">
          <h1 className="text-base font-medium">Configuration</h1>
          <div className="flex items-center gap-2">
            <TenantSelect />
            <SaveButton />
            <HistoryButton />
          </div>
        </div>
      </SiteHeader>
      <div className="flex flex-1 flex-col gap-4 p-4">
        <DataTable
          data={[
            {
              id: 1,
              importance: 100,
              name: "title",
              strategies: ["Precise", "Fuzzy"],
            },
            {
              id: 2,
              importance: 50,
              name: "artNr",
              strategies: [],
            },
            {
              id: 4,
              importance: 50,
              name: "test1",
              strategies: [],
            },
            {
              id: 5,
              importance: 50,
              name: "test2",
              strategies: [],
            },
            {
              id: 6,
              importance: 50,
              name: "test3",
              strategies: [],
            },
            {
              id: 7,
              importance: 50,
              name: "test4",
              strategies: [],
            },
            {
              id: 8,
              importance: 50,
              name: "test5",
              strategies: [],
            },
            {
              id: 9,
              importance: 50,
              name: "test6",
              strategies: [],
            },
            {
              id: 10,
              importance: 50,
              name: "test7",
              strategies: [],
            },
            {
              id: 11,
              importance: 50,
              name: "test8",
              strategies: [],
            },
            {
              id: 12,
              importance: 50,
              name: "test9",
              strategies: [],
            },
          ]}
        />
      </div>
    </>
  );
}
