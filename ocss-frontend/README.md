# ocss-frontend

Frontend for the [open-commerce-search-stack](https://github.com/CommerceExperts/open-commerce-search).

# Getting Started

## Docker (production)

You might use the published image 'commerceexperts/ocss-frontend:latest'

To build custom image do the following steps:

```
cd ocss-frontend
```

```
docker build -t ocss-frontend .
```

```
docker run -e SUGGEST_API_AUTH="" -e SUGGEST_API_URL="" -e SEARCH_API_AUTH="" -e SEARCH_API_URL="" -e BASEPATH="" -e DEFAULT_TENANT="" -e GITHUB_OAUTH_ID="" -e GITHUB_OAUTH_SECRET="" -e NEXTAUTH_SECRET="" -e NEXTAUTH_URL="" --rm -p 3000:3000 ocss-frontend
```

## Node.js (development)

```
git clone https://github.com/CommerceExperts/open-commerce-search
```

```
cd ocss-frontend
```

```
npm i
```

> Create .env file with environment variables.

```
npm run dev
```

# Documentation

## Environment Variables

### Suggest API

| Name               | Description                                                                            |
| ------------------ | -------------------------------------------------------------------------------------- |
| SUGGEST_API_AUTH   | Auth for suggest api encoded in base64 (leave blank if no authentication is required). |
| SUGGEST_API_URL    | URL of suggest api endpoint.                                                           |
| ENABLE_SUGGEST_API | Suggest API is optional. Set "true" to activate or "false" to deactivate.              |

### Search API

| Name            | Description                                                                           |
| --------------- | ------------------------------------------------------------------------------------- |
| SEARCH_API_AUTH | Auth for search api encoded in base64 (leave blank if no authentication is required). |
| SEARCH_API_URL  | URL of search api endpoint.                                                           |

### Miscellaneous

| Name                      | Description                                                                                                                               |
| ------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| DEFAULT_TENANT            | Tenant that is being used if no specific tenant is set on frontend.                                                                       |
| BASEPATH                  | Basepath of application (do not set environment variable if not required).                                                                |
| DEFAULT_CONFIG_REPO_OWNER | Default repository owner for configuration. Needed if DEFAULT_CONFIG_REPO_NAME is set. (do not set environment variable if not required). |
| DEFAULT_CONFIG_REPO_NAME  | Default repository name for configuration. Needed if DEFAULT_CONFIG_REPO_OWNER is set. (do not set environment variable if not required). |
| PRODUCTSETSERVICE_BASEURL | Base url of [product set service](https://github.com/CommerceExperts/ocss-curator-service).                                               |
| INDEXER_CONFIG_FILE_PATH  | Optional indexer config file path (default is "indexer-service.yml")                                                                      |
| SEARCH_CONFIG_FILE_PATH   | Optional searcher config file path (default is "search-service.yml")                                                                      |

### Authentication

| Name                | Description                                                                                                                                                   |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| GITHUB_OAUTH_ID     | Client ID of your Github OAuth app (<https://github.com/settings/developers>).                                                                                |
| GITHUB_OAUTH_SECRET | Client secret of your Github OAuth app (<https://github.com/settings/developers>).                                                                            |
| NEXTAUTH_SECRET     | Secret for JWT and hashing. Use a secure, randomly-generated secret (<https://next-auth.js.org/configuration/options>).                                       |
| NEXTAUTH_URL        | When deploying to production, set the NEXTAUTH_URL environment variable to the canonical URL of your site (<https://next-auth.js.org/configuration/options>). |

> Authentication environment variables are just needed for the ocss configurator.

[See .env.example](https://github.com/CommerceExperts/open-commerce-search/blob/master/ocss-frontend/.env.example)
