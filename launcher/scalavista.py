from subprocess import Popen
import glob
import json
import os
import requests
import time
import crayons
import platform

# make terminal colors work on windows
from colorama import init
init()


def info(msg):
    print('{}#{} {}'.format('scalavista', crayons.magenta('info'), msg))


def success(msg):
    print('{}#{} {}'.format('scalavista', crayons.green('success'), msg))


def warn(msg):
    print('{}#{} {}'.format('scalavista', crayons.yellow('warn'), msg))


def error(msg):
    print('{}#{} {}'.format('scalavista', crayons.red('error'), msg))


def launch(port, scalac_opts='', debug=False, trace=False, recursive=False):

    if platform.system() == 'Windows':
        sep = ';'
    else:
        sep = ':'

    try:
        with open('scalavista.json') as f:
            conf = json.load(f)
            scala_binary_version = conf['scalaBinaryVersion']
            classpath = sep.join(conf['classpath'])
            sources = conf['sources']
            if not scalac_opts:
                scalac_opts = ' '.join(conf['scalacOptions'])
    except IOError:
        warn('missing "scalavista.json" - you can generate it using the scalavista sbt-plugin.')
        scala_binary_version = '2.12'
        lib_jars = glob.glob(os.path.join(os.path.abspath('./lib'), '*.jar'))
        classpath = sep.join(lib_jars)
        if recursive:
            sources = []
            for root, _, files in os.walk(os.path.abspath('.')):
                for file in files:
                    if file.endswith('.scala'):
                        sources.append(os.path.join(root, file))
        else:
            sources = [os.path.abspath(source_path) for source_path in glob.glob('*.scala')]

    base_dir = os.path.dirname(os.path.realpath(__file__))
    jar_wildcard = os.path.join(base_dir, 'jars', r'scalavista-server*_{}.jar'.format(scala_binary_version))

    if not glob.glob(jar_wildcard):
        raise RuntimeError('no suitable scalavista jar found - run install.sh')

    scalavista_jar = glob.glob(jar_wildcard)[0]

    if not os.path.isfile(scalavista_jar):
        raise RuntimeError('jar not found: {}'.format(scalavista_jar))

    if classpath:
        classpath = scalavista_jar + sep + classpath
    else:
        classpath = scalavista_jar

    call = ['java', '-cp', classpath, 'org.scalavista.ScalavistaServer', '--port', str(port)]

    call.extend(['--scalacopts', '"{}"'.format(scalac_opts)])

    if debug:
        call.append('--debug')

    if trace:
        call.append('--trace')

    info('launching server...')
    server_process = Popen(call)
    server_url = 'http://localhost:{}'.format(port)

    max_tries = 10
    for i in range(max_tries):
        try:
            info('testing connection...({}/{})'.format(i + 1, max_tries))
            req = requests.get(server_url + '/alive')
        except Exception:
            time.sleep(2)
        else:
            if req.status_code == requests.codes.ok:
                break
    else:
        server_process.terminate()
        error('failed to start server - quitting...')
        return

    filenames = []
    file_contents = []
    for source_file in sources:
        if not source_file.endswith('.scala'):
            warn('non-Scala-source file detected: {}'.format(source_file))
            continue
        with open(source_file) as f:
            filenames.append(source_file)
            file_contents.append(f.read())
    try:
        source_data = {'filenames': filenames, 'fileContents': file_contents}
        res = requests.post(server_url + '/reload-files', json=source_data)
        if res.status_code != requests.codes.ok:
            raise Exception
    except Exception:
        warn('failed to load sources')

    success('server is up and running at {} - press any key to stop...'.format(server_url))

    input('')

    server_process.terminate()
