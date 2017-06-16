import argparse
import json
import os
import re
import subprocess
import time


verbose = True


def info(string):
    if verbose:
        print('INFO: ' + string)


def warn(string):
    if verbose:
        print('WARN: ' + string)


def parse_arguments():
    global verbose
    parser = argparse.ArgumentParser()
    parser.add_argument('-q', '--quite', help='Quite mode',
                        action='store_true')
    parser.add_argument('-n', '--new', help='Create a new cluster',
                        action='store_true')
    parser.add_argument('-u', '--user', help='Username of Github repo owner')
    parser.add_argument('-b', '--branch', help='Branch to deploy')
    parser.add_argument('-p', '--project', help='GCP project ID',
                        required=True)

    args = parser.parse_args()

    if args.quite:
        verbose = False

    parser_config = {
        'cloud-config': {
            'user': args.user if args.user else 'loklak',
            'branch': args.branch if args.branch else 'development',
            'project': args.project
        },
        'create-cluster': args.new
    }

    return parser_config


def clone_project(user):
    subprocess.check_output(['rm', '-rdf', 'loklak_server'])
    git_url = 'https://github.com/%s/loklak_server.git' % user
    info('Cloning %s...' % git_url)
    output = subprocess.check_output(['git', 'clone', git_url, '-q'])
    info('Successfully cloned %s- \n%s' % (git_url, output))
    os.chdir('loklak_server')


def checkout_branch(branch):
    output = subprocess.check_output(['git', 'checkout', 'origin/%s' % branch])
    info(output)


def build_docker_image(project, version):
    tag = 'gcr.io/%s/loklak:%s' % (project, version)
    command = 'docker build -t %s .' % tag
    info('Building image with TAG %s' % tag)
    output = subprocess.check_output(command.split())
    info('Docker build complete - \n%s' % output)


def push_docker_image(project, version):
    tag = 'gcr.io/%s/loklak:%s' % (project, version)
    info('Pushing docker image to GCR...')
    output = subprocess.check_output(['gcloud', 'docker', '--', 'push', tag])
    info('Pushed %s to GCR - \n%s' % (tag, output))


def create_cluster(cluster_name, num_nodes, machine_type, zone):
    info('Creating Cluster...')
    output = subprocess.check_output(
        ['gcloud', 'container', 'clusters', 'create', cluster_name,
         '--num-nodes', num_nodes, '--machine-type', machine_type,
         '--zone', zone])
    info('Cluster created successfully - \n%s' % output)


def run_deployment(deployment, project, version):
    tag = 'gcr.io/%s/loklak:%s' % (project, version)
    info('Running deployment %s using tag %s ' % (deployment, tag))
    output = subprocess.check_output(['kubectl', 'run', deployment,
                                      '--image=%s' % tag, '--port=80'])
    info(output)


def expose_deployment(deployment):
    info('Exposing deployment %s' % deployment)
    output = subprocess.check_output(['kubectl', 'expose', 'deployment',
                                      deployment, '--type=LoadBalancer'])
    info(output)


def fetch_public_ip(deployment):
    info('Polling to get public IP for loklak')
    regex_ip = re.compile(r'%s[\t\ ]+[0-9]+\.[0-9]+\.[0-9]+\.[0-9]'
                          r'+[\t\ ]+([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)'
                          % deployment)
    while True:
        output = subprocess.check_output(['kubectl', 'get', 'services'])
        lines = output.split('\n')
        found_flag = False
        for line in lines:
            if line.startswith(deployment):
                found_flag = True
                findings = regex_ip.findall(line)
                if findings:
                    return findings[0]
        if not found_flag:
            raise Exception('No service named "loklak" found')
        time.sleep(10)


def get_credentials(cluster, zone, project):
    info('Fetching credentials for cluster %s...' % cluster)
    output = subprocess.check_output(
        ['gcloud', 'container', 'clusters', 'get-credentials',
         cluster, '--zone', zone, '--project', project])
    info('Fetched credentials for %s - \n%s' % (cluster, output))


def apply_config_changes(properties):
    for i in properties:
        info('Changing property %s to %s' % (i, properties[i]))
        subprocess.check_output([
            'sed', '-i', 's/^\(%s=\).*/\\1%s/' % (i, properties[i]),
            'conf/config.properties'])


def get_config_from_repo():
    try:
        with open('google-cloud-kubernetes/config.json', 'rb') as fd:
            conf = json.load(fd)
            info('Loaded configuration: %s' % conf)
            return conf
    except IOError as e:
        warn('Unable to open file "google-cloud-kubernetes/config.json": %s'
             % e)
        return {}


def start_deploy():
    config = parse_arguments()

    user = config['cloud-config'].get('user', 'loklak')
    branch = config['cloud-config'].get('branch', 'development')

    clone_project(user)
    checkout_branch(branch)
    config.update(get_config_from_repo())

    project = config['cloud-config']['project']
    version = config.get('version', 'v1')
    cluster = config.get('cluster', 'loklak-cluster')
    num_nodes = config.get('num-nodes', '2')
    node_type = config.get('node-type', 'n1-standard-4')
    zone = config.get('zone', 'us-central1-a')
    deployment = config.get('deployment', 'loklak')
    config_changes = config.get('config-changes', {})

    if config['create-cluster']:
        create_cluster(cluster, num_nodes, node_type, zone)
    else:
        get_credentials(cluster, zone, project)

    apply_config_changes(config_changes)
    build_docker_image(project, version)
    push_docker_image(project, version)
    run_deployment(deployment, project, version)
    expose_deployment(deployment)
    print(fetch_public_ip(deployment))


if __name__ == '__main__':
    try:
        start_deploy()
    except KeyboardInterrupt:
        info('Deployment process cancelled by user')
