import {
  Card,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import {
  IconBook,
  IconBrandGithub,
  IconDatabase,
  IconRefresh,
  IconSearch,
  IconTrash,
} from "@tabler/icons-react";
import { Badge } from "@/components/ui/badge";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { ScrollArea } from "@/components/ui/scroll-area";
import { cn } from "@/lib/utils";

export function WelcomeMessageCard(props: React.ComponentProps<"div">) {
  return (
    <Card {...props}>
      <CardHeader>
        <CardTitle className="text-2xl">
          <span className="font-medium">Welcome,</span> Nelson F.
        </CardTitle>
        <CardDescription>
          The Open Commerce Search Stack (OCSS) is a small abstraction layer on
          top of existing open source search solutions (Elasticsearch, Lucene
          and Querqy) that removes the hastle of dealing with the details of
          information retrieval. It was designed to handle most problems of
          search in e-commerce using known best practices.
        </CardDescription>
      </CardHeader>
      <CardFooter className="space-x-2">
        <Button>
          <IconBook />
          <span>Read Documentation</span>
        </Button>
        <Button variant="secondary">
          <IconBrandGithub />
          <span>See Source Code</span>
        </Button>
      </CardFooter>
    </Card>
  );
}

export function IndexListCard(props: React.ComponentProps<"div">) {
  return (
    <Card {...props} className={cn(props.className, "gap-0")}>
      <CardHeader>
        <CardTitle className="text-xl">Indexes</CardTitle>
      </CardHeader>
      <ScrollArea className="h-[350px] px-6">
        <Accordion type="single" collapsible>
          {[...Array(100).keys()]
            .map((i) => i.toString())
            .map((i) => (
              <AccordionItem value={i}>
                <AccordionTrigger>
                  <p className="flex gap-2 items-center justify-start">
                    <IconDatabase className="w-4 h-4" />
                    <span>my-shop-{i}</span>
                  </p>
                </AccordionTrigger>
                <AccordionContent className="space-y-4">
                  <p className="text-muted-foreground">1 document</p>
                  <div className="flex gap-2">
                    <Button size="sm">
                      <IconSearch />
                      <span>Search</span>
                    </Button>

                    <Button size="sm" variant="destructive">
                      <IconTrash />
                      <span>Delete</span>
                    </Button>
                  </div>
                </AccordionContent>
              </AccordionItem>
            ))}
        </Accordion>
      </ScrollArea>
    </Card>
  );
}

export function SearchServiceStatusCard(props: React.ComponentProps<"div">) {
  return (
    <Card
      {...props}
      className={cn(props.className, "flex flex-col justify-between")}
    >
      <CardHeader>
        <CardTitle className="text-lg flex items-center gap-2">
          <span>Search Service</span>
          <Badge variant="outline" className="flex gap-2 items-center">
            <div className="w-[5px] h-[5px] rounded-full bg-green-500" />
            Online
          </Badge>
        </CardTitle>
        <p className="text-muted-foreground">http://localhost:8534</p>
      </CardHeader>
      <CardFooter>
        <Button className="w-full" size="sm" variant="outline">
          <IconRefresh />
          <span>Refresh</span>
        </Button>
      </CardFooter>
    </Card>
  );
}

export function IndexServiceStatusCard(props: React.ComponentProps<"div">) {
  return (
    <Card
      {...props}
      className={cn(props.className, "flex flex-col justify-between")}
    >
      <CardHeader>
        <CardTitle className="text-lg flex items-center gap-2">
          <span>Index Service</span>
          <Badge variant="outline" className="flex gap-2 items-center">
            <div className="w-[5px] h-[5px] rounded-full bg-red-500" />
            Offline
          </Badge>
        </CardTitle>
        <p className="text-muted-foreground">http://localhost:8535</p>
      </CardHeader>
      <CardFooter>
        <Button className="w-full" size="sm" variant="outline">
          <IconRefresh />
          <span>Refresh</span>
        </Button>
      </CardFooter>
    </Card>
  );
}

export function ElasticsearchStatusCard(props: React.ComponentProps<"div">) {
  return (
    <Card {...props} className={cn(props.className, "flex flex-col justify-between")}>
      <CardHeader>
        <CardTitle className="text-lg flex items-center gap-2">
          <span>Elasticsearch</span>
          <Badge variant="outline" className="flex gap-2 items-center">
            <div className="w-[5px] h-[5px] rounded-full bg-green-500" />
            Online
          </Badge>
        </CardTitle>
        <p className="text-muted-foreground">http://localhost:9000</p>
      </CardHeader>
      <CardFooter>
        <Button className="w-full" size="sm" variant="outline">
          <IconRefresh />
          <span>Refresh</span>
        </Button>
      </CardFooter>
    </Card>
  );
}
