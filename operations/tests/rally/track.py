from .custom_runner.ocss_search_runner import OCSSSearchRunner

# register custom runners here


def register(registry):
    registry.register_runner("ocss-search", OCSSSearchRunner(), async_runner=True)