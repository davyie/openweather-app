gcloud run deploy openweather-app \
--source . \
--network=starter-vpc \
--subnet=private-subnet \
--vpc-egress=private-ranges-only \
--region=europe-north2 \
--env-vars-file=env.yaml