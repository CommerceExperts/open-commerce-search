# -*- coding: utf-8 -*-

import json


class OCSSSearchRunner:
    """
    This runner should perform searches from ocss search logfile against elastic
    """

    # define search data
    search_data = []

    def initialize(self, params):
        # check given parameter
        if "index" in params and type(params["index"]) is str:
            self.index = params["index"]
        else:
            raise RuntimeError from None
        if "source-file" not in params or type(params["source-file"]) is not str:
            print("ERROR no source data file given, or wrong format", end=". ")
            raise RuntimeError from None

        # load / check search data
        if self.search_data is None or len(self.search_data) < 1:
            with open(params["source-file"]) as json_file:
                for line in json_file:
                    self.search_data.append(json.loads(line))

    async def __call__(self, es, params):
        self.initialize(params=params)
        # perform search here
        search = self.search_data.pop()
        search_body = search["query"]
        search_response = await es.search(body=search_body, index=self.index)

        # get time
        return search_response["took"], "ms"

    def __repr__(self, *args, **kwargs):
        return "ocss-search"
