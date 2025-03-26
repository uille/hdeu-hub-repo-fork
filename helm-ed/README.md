# HDEU Hub Repo Project Helm Charts

## Table of Contents
- [HDEU Hub Repo Project Helm-Charts](#hdeu-hub-repo-project-helm-charts)
- [Kubernetes Version Support](#kubernetes-version-support)
- [Installation](#installation)
- [Configuration](#configuration)
- [AWS Secrets Manager Integration](#aws-secrets-manager-integration)
  - [How It Works](#how-it-works)
  - [SecretProviderClass Definition](#secretproviderclass-definition)
  - [Mapping AWS Secrets to Kubernetes Secrets](#mapping-aws-secrets-to-kubernetes-secrets)
  - [How to Use](#how-to-use)
- [License](#license)

## HDEU Hub Repo Project Helm Charts

A community repository for Helm Charts of HDEU Hub Repo Project.

## Kubernetes Version Support

* This Helm chart repository is tested with Kubernetes version 1.29 and above.

## Installation

To install via Helm, run the following command:

```shell
helm upgrade --install release-name . -f values.yaml -n default
```

## Configuration

The Helm chart can be customized using the `values.yaml` file. Modify this file to set parameters according to your requirements before installation.

You can also override values at the time of installation using the `--set` flag. For example:

```shell
helm upgrade --install release-name . -f values.yaml --set key=value -n default
```

For a full list of configurable parameters, refer to `values.yaml`.

## AWS Secrets Manager Integration

This Helm chart supports integration with **AWS Secrets Manager** using the **Secrets Store CSI Driver** to securely retrieve secrets and expose them as Kubernetes secrets.

### How It Works

- AWS Secrets Manager stores sensitive configuration values.
- The **SecretProviderClass** defines which secrets to retrieve and how they are mapped.
- The retrieved secrets are automatically created as **Kubernetes secrets** and mounted into the application as environment variables.

### SecretProviderClass Definition

The following **SecretProviderClass** is created when `aws.enabled` is set to `true`:

```yaml
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: <release-name>-spc
spec:
  provider: aws
  parameters:
    objects: |
      - objectName: "HEALTHDATA_VIRTUOSO_SECRET"
        objectType: "secretsmanager"
        jmesPath:
          - path: username
            objectAlias: username
          - path: password
            objectAlias: password
          - path: virtuoso_config_url
            objectAlias: virtuoso_config_url
          - path: dba_password
            objectAlias: dba_password
      - objectName: "HEALTHDATA_SHADOW_VIRTUOSO_SECRET"
        objectType: "secretsmanager"
        jmesPath:
          - path: shadow_username
            objectAlias: shadow_username
          - path: shadow_password
            objectAlias: shadow_password
          - path: shadow_virtuoso_config_url
            objectAlias: shadow_virtuoso_config_url
          - path: shadow_dba_password
            objectAlias: shadow_dba_password
      - objectName: "HEALTHDATA_API_KEYS"
        objectType: "secretsmanager"
        jmesPath:
          - path: hub_repo_api_key
            objectAlias: hub_repo_api_key
          - path: hub_search_service_api_key
            objectAlias: hub_search_service_api_key
          - path: hub_store_data_upload_api_key
            objectAlias: hub_store_data_upload_api_key
  secretObjects:
    - secretName: <release-name>-secrets
      type: Opaque
      data:
      - objectName: username
        key: username
      - objectName: password
        key: password
      - objectName: virtuoso_config_url
        key: virtuoso_config_url
      - objectName: shadow_username
        key: shadow_username
      - objectName: shadow_password
        key: shadow_password
      - objectName: shadow_virtuoso_config_url
        key: shadow_virtuoso_config_url
      - objectName: dba_password
        key: dba_password
      - objectName: shadow_dba_password
        key: shadow_dba_password
      - objectName: hub_repo_api_key
        key: hub_repo_api_key
      - objectName: hub_search_service_api_key
        key: hub_search_service_api_key
      - objectName: hub_store_data_upload_api_key
        key: hub_store_data_upload_api_key
```

### Mapping AWS Secrets to Kubernetes Secrets

| AWS Secret Name                     | AWS Key (path)              | Kubernetes Secret Key          |
|--------------------------------------|-----------------------------------|--------------------------------|
| `HEALTHDATA_VIRTUOSO_SECRET`         | `username`                        | `username`                     |
|                                      | `password`                        | `password`                     |
|                                      | `virtuoso_config_url`             | `virtuoso_config_url`          |
|                                      | `dba_password`                    | `dba_password`                 |
| `HEALTHDATA_SHADOW_VIRTUOSO_SECRET`  | `shadow_username`                 | `shadow_username`              |
|                                      | `shadow_password`                 | `shadow_password`              |
|                                      | `shadow_virtuoso_config_url`      | `shadow_virtuoso_config_url`   |
|                                      | `shadow_dba_password`             | `shadow_dba_password`          |
| `HEALTHDATA_API_KEYS`                | `hub_repo_api_key`                | `hub_repo_api_key`             |
|                                      | `hub_search_service_api_key`      | `hub_search_service_api_key`   |
|                                      | `hub_store_data_upload_api_key`   | `hub_store_data_upload_api_key` |

### How to Use

1. Ensure that **AWS Secrets Manager** is configured with the required secrets.
2. Set `aws.enabled: true` in `values.yaml`.
3. Deploy the Helm chart.
4. The application will retrieve secrets automatically via the CSI driver.

For more details, refer to:

- [AWS Secrets Manager Documentation](https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html)
- [Secrets Store CSI Driver Documentation](https://docs.aws.amazon.com/secretsmanager/latest/userguide/integrating_csi_driver.html)

## License

This project is licensed under the [EUROPEAN UNION PUBLIC LICENCE v. 1.2](https://eupl.eu/1.2/en/)
