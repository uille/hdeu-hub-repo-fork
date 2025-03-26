{{/*
Expand the name of the chart.
*/}}
{{- define "piveau-hub-repo.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "piveau-hub-repo.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else }}
{{- include "piveau-hub-repo.name" . | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "piveau-hub-repo.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | replace "." "_" -}}
{{- end -}}

{{/*
Common labels
*/}}
{{- define "piveau-hub-repo.labels" -}}
helm.sh/chart: {{ include "piveau-hub-repo.chart" . }}
{{- with .Chart.AppVersion }}
app.kubernetes.io/version: {{ . | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/*
Selector labels
*/}}
{{- define "piveau-hub-repo.selectorLabels" -}}
app.kubernetes.io/name: {{ include "piveau-hub-repo.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}


{{/*
Create the name of the service account to use
*/}}
{{- define "piveau-hub-repo.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "piveau-hub-repo.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- "default" }}
{{- end -}}
{{- end -}}


{{/*
Create a name for the virtuoso triple store
*/}}
{{- define "piveau-hub-repo.virtuosoName" -}}
{{- $name := "virtuoso" }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}


{{/*
Virtuoso common labels
*/}}
{{- define "piveau-hub-repo.virtuosoLabels" -}}
helm.sh/chart: {{ include "piveau-hub-repo.chart" . }}
{{ include "piveau-hub-repo.virtuosoSelectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Virtuoso selector labels
*/}}
{{- define "piveau-hub-repo.virtuosoSelectorLabels" -}}
app.kubernetes.io/name: "virtuoso"
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "piveau-hub-repo.virtuosoServiceAccountName" -}}
{{- if .Values.virtuoso.serviceAccount.create }}
{{- default (include "piveau-hub-repo.virtuosoName" .) .Values.virtuoso.serviceAccount.name }}
{{- else }}
{{- "default" }}
{{- end -}}
{{- end -}}



{{/*
Create a name for the shadow-virtuoso triple store
*/}}
{{- define "piveau-hub-repo.shadowVirtuosoName" -}}
{{- $name := "shadow-virtuoso" }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}


{{/*
Shadow Virtuoso common labels
*/}}
{{- define "piveau-hub-repo.shadowVirtuosoLabels" -}}
helm.sh/chart: {{ include "piveau-hub-repo.chart" . }}
{{ include "piveau-hub-repo.shadowVirtuosoSelectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Shadow Virtuoso selector labels
*/}}
{{- define "piveau-hub-repo.shadowVirtuosoSelectorLabels" -}}
app.kubernetes.io/name: "shadow-virtuoso"
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "piveau-hub-repo.shadowVirtuosoServiceAccountName" -}}
{{- if .Values.shadowVirtuoso.serviceAccount.create }}
{{- default (include "piveau-hub-repo.shadowVirtuosoName" .) .Values.shadowVirtuoso.serviceAccount.name }}
{{- else }}
{{- "default" }}
{{- end -}}
{{- end -}}

