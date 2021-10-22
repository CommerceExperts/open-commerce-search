# Docs Writing Help

To test the final output, a jekyll server can be started via docker that publishes the docs at `localhost:4000`.
Start the docker container from this 'docs' directory like that:

    docker run -it --name "jekyll-ocss-docs" --volume="$PWD/vendor/bundle:/usr/local/bundle" --volume="$PWD:/srv/jekyll" --publish [::1]:4000:4000 jekyll/jekyll bash

Inside the started container you get an interactive shell. Run these commands:

    bundle install
    bundle exec jekyll serve -H 0.0.0.0 --livereload --incremental

If you won't delete the container, you can simply start it again and run the last command again. This avoids the long running 'bundle install' step.
